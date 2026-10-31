package com.smu.cs203_g1_1.itinerary_planner.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gate and bounded memo for cold-start backfill.
 *
 * <p>A point is backfilled at most once per hour. The memo key is a rounded
 * ~100 m cell rather than the exact coordinate, so tiny GPS differences do not
 * each trigger an Overpass query. The map is access-ordered and capped so a
 * long-running process cannot leak memory.</p>
 */
@Component
public class BackfillPolicy {

    static final Duration MEMO_TTL = Duration.ofHours(1);
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    /** About 111 m per cell at the equator. */
    private static final double CELL_DEGREES = 1_000.0;

    private final Clock clock;
    private final boolean enabled;
    private final int maxEntries;
    private final Map<Cell, Instant> lastBackfill = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Cell, Instant> eldest) {
            return size() > maxEntries;
        }
    };

    @Autowired
    public BackfillPolicy(Clock clock,
                          @Value("${app.discovery.backfill-enabled:true}") boolean enabled) {
        this(clock, enabled, DEFAULT_MAX_ENTRIES);
    }

    BackfillPolicy(Clock clock, boolean enabled, int maxEntries) {
        this.clock = clock;
        this.enabled = enabled;
        this.maxEntries = Math.max(1, maxEntries);
    }

    /** True when backfill is enabled and this cell has not run in the last hour. */
    public boolean shouldBackfill(double latitude, double longitude) {
        if (!enabled) {
            return false;
        }

        Instant cutoff = clock.instant().minus(MEMO_TTL);
        Cell cell = cell(latitude, longitude);

        synchronized (lastBackfill) {
            lastBackfill.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            Instant last = lastBackfill.get(cell);
            return last == null || last.isBefore(cutoff);
        }
    }

    /** Records that a backfill ran for this point's cell now. */
    public void recordBackfill(double latitude, double longitude) {
        Cell cell = cell(latitude, longitude);
        Instant now = clock.instant();

        synchronized (lastBackfill) {
            lastBackfill.put(cell, now);
        }
    }

    private static Cell cell(double latitude, double longitude) {
        return new Cell(
            Math.round(latitude * CELL_DEGREES),
            Math.round(longitude * CELL_DEGREES));
    }

    private record Cell(long latitude, long longitude) {
    }
}
