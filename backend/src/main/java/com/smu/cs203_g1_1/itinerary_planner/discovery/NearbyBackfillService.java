package com.smu.cs203_g1_1.itinerary_planner.discovery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smu.cs203_g1_1.itinerary_planner.geo.PoiService;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationService;

import jakarta.annotation.PreDestroy;

/**
 * Cold-start enrichment: pulls nearby OSM places into the local {@code location}
 * table so the planner has rows to read on the next pass.
 *
 * <p>Every import goes through {@link LocationService#findOrCreateFromOsm}, which
 * is idempotent by OSM identity and already sets classification, category group
 * and opening hours. Failures are logged and swallowed: backfill is opportunistic
 * and must never break a replan.</p>
 */
@Service
public class NearbyBackfillService {

    private static final Logger log = LoggerFactory.getLogger(NearbyBackfillService.class);

    /** Same whitelist {@link PoiService} supports; ordered by expected usefulness. */
    private static final List<String> CATEGORIES = List.of(
        "restaurant",
        "cafe",
        "attraction",
        "museum",
        "bar",
        "fast_food",
        "hotel",
        "hostel");

    private final PoiService poiService;
    private final LocationService locationService;
    private final int maxCategories;
    private final Executor executor;

    @Autowired
    public NearbyBackfillService(PoiService poiService,
                                 LocationService locationService,
                                 @Value("${app.discovery.backfill-max-categories:4}")
                                 int maxCategories,
                                 @Value("${app.discovery.backfill-queue-capacity:16}")
                                 int queueCapacity) {
        this(poiService, locationService, maxCategories,
            createExecutor(queueCapacity));
    }

    /** Direct-executor overload for existing tests that only call {@link #backfill}. */
    public NearbyBackfillService(PoiService poiService,
                                 LocationService locationService,
                                 int maxCategories) {
        this(poiService, locationService, maxCategories, Runnable::run);
    }

    NearbyBackfillService(PoiService poiService,
                          LocationService locationService,
                          int maxCategories,
                          Executor executor) {
        this.poiService = poiService;
        this.locationService = locationService;
        this.maxCategories = Math.max(0, maxCategories);
        this.executor = executor;
    }

    /**
     * Persists up to {@code limit} OSM places around the point and returns how
     * many were imported successfully.
     */
    public int backfill(double latitude, double longitude, int radiusM, int limit) {
        return runBackfill(latitude, longitude, radiusM, limit);
    }

    /**
     * Submits the backfill and returns immediately; completed with the imported
     * count. Rejected or failing work completes with {@code 0} rather than
     * throwing.
     */
    public CompletableFuture<Integer> backfillAsync(double latitude, double longitude,
                                                    int radiusM, int limit) {
        if (limit <= 0 || maxCategories == 0) {
            return CompletableFuture.completedFuture(0);
        }

        CompletableFuture<Integer> result = new CompletableFuture<>();

        // Serialize submit-vs-full so DiscardOldestPolicy cannot silently swap
        // work out under a concurrent producer; full means reject here.
        synchronized (executor) {
            if (isQueueFull()) {
                log.debug("Backfill queue full at {},{}; skipping", latitude, longitude);
                return CompletableFuture.completedFuture(0);
            }

            try {
                executor.execute(() -> {
                    try {
                        result.complete(runBackfill(latitude, longitude, radiusM, limit));
                    } catch (RuntimeException ex) {
                        log.warn("Async backfill failed at {},{}: {}",
                            latitude, longitude, ex.getMessage());
                        result.complete(0);
                    }
                });
            } catch (RejectedExecutionException ex) {
                log.debug("Backfill submission rejected at {},{}; skipping",
                    latitude, longitude);
                return CompletableFuture.completedFuture(0);
            }
        }

        return result;
    }

    @PreDestroy
    public void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    private int runBackfill(double latitude, double longitude, int radiusM, int limit) {
        if (limit <= 0 || maxCategories == 0) {
            return 0;
        }

        int categoryCount = Math.min(maxCategories, CATEGORIES.size());
        Set<String> seen = new HashSet<>();
        int handled = 0;
        int imported = 0;

        for (int index = 0; index < categoryCount && handled < limit; index++) {
            String category = CATEGORIES.get(index);
            int remaining = limit - handled;

            List<PlaceDto> places;
            try {
                places = poiService.findPois(latitude, longitude, radiusM, category, remaining);
            } catch (RuntimeException ex) {
                log.warn("Backfill lookup failed for category {} at {},{}: {}",
                    category, latitude, longitude, ex.getMessage());
                continue;
            }

            if (places == null) {
                continue;
            }

            for (PlaceDto place : places) {
                if (handled >= limit) {
                    break;
                }
                handled++;

                if (!hasOsmIdentity(place)) {
                    continue;
                }

                String key = place.osmType() + ":" + place.osmId();
                if (!seen.add(key)) {
                    continue;
                }

                try {
                    locationService.findOrCreateFromOsm(place.osmType(), place.osmId());
                    imported++;
                } catch (RuntimeException ex) {
                    log.warn("Backfill import failed for osm {}:{}: {}",
                        place.osmType(), place.osmId(), ex.getMessage());
                }
            }
        }

        log.info("Backfill at {},{} imported={} handled={} categories={}",
            latitude, longitude, imported, handled, categoryCount);
        return imported;
    }

    private boolean isQueueFull() {
        if (!(executor instanceof ThreadPoolExecutor pool)) {
            return false;
        }
        return pool.getQueue().remainingCapacity() == 0;
    }

    private static Executor createExecutor(int queueCapacity) {
        int capacity = Math.max(1, queueCapacity);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacity),
            runnable -> {
                Thread thread = new Thread(runnable, "backfill-worker");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());
        return pool;
    }

    private static boolean hasOsmIdentity(PlaceDto place) {
        return place != null
            && place.osmType() != null
            && !place.osmType().isBlank()
            && place.osmId() != null;
    }
}
