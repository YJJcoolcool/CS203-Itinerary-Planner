package com.smu.cs203_g1_1.itinerary_planner.polling;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionService;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.TriggerSource;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

/**
 * Proactive rain-driven replanning.
 *
 * <p>Two independent ticks:
 * <ul>
 *   <li>hourly: trips starting tomorrow, gated by the centroid of their stops;</li>
 *   <li>every ten minutes by default: activities starting in 90–150 minutes.</li>
 * </ul>
 *
 * <p>Both check the weather <em>before</em> calling the suggestion service, so a
 * dry tick performs no discovery and no LLM call. The gate call plus the
 * service's per-activity re-check is intentional: the gate avoids discovery and
 * LLM work for an obviously dry tick, while the service owns the final
 * eligibility rules (outdoor stop, window, pending/rejected suppression). Each
 * trip/activity is isolated in its own try/catch: one bad row must never abort
 * the whole tick.</p>
 *
 * <p>Note: {@code replanForSystem} is trip-scoped, so a 2 h tick can also
 * propose for other eligible stops in the same trip. That is accepted; it is not
 * a per-activity suggestion path.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.polling", name = "enabled", havingValue = "true")
public class WeatherPoller {

    private static final Logger log = LoggerFactory.getLogger(WeatherPoller.class);

    /*
     * The tick zone is immaterial: ticks are hourly and the day window is
     * derived per trip from its region's zone below. Do not "fix" this to a
     * per-trip cron; Spring cannot schedule per row.
     */
    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Asia/Singapore");

    private final TripRepository tripRepository;
    private final ActivityRepository activityRepository;
    private final SuggestionService suggestionService;
    private final WeatherSourceResolver weatherSourceResolver;
    private final RegionRegistry regionRegistry;
    private final Clock clock;

    public WeatherPoller(TripRepository tripRepository,
                         ActivityRepository activityRepository,
                         SuggestionService suggestionService,
                         WeatherSourceResolver weatherSourceResolver,
                         RegionRegistry regionRegistry,
                         Clock clock) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.suggestionService = suggestionService;
        this.weatherSourceResolver = weatherSourceResolver;
        this.regionRegistry = regionRegistry;
        this.clock = clock;
    }

    /** 24 h pass: trips whose start date is tomorrow in their own region. */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Singapore")
    public void pollTomorrowTrips() {
        LocalDate scanDate = LocalDate.now(clock.withZone(SCHEDULE_ZONE)).plusDays(1);
        List<Trip> trips = tripRepository.findByStartDate(scanDate);

        int tripsScanned = 0;
        int activitiesScanned = 0;
        int suggestionsCreated = 0;

        for (Trip trip : trips) {
            tripsScanned++;
            try {
                Optional<RegionProfile> region = regionRegistry.byCode(trip.getCountryCode());
                if (region.isEmpty()) {
                    log.info("Skipping trip id={} for polling: no region for country {}",
                            trip.getId(), trip.getCountryCode());
                    continue;
                }

                Optional<WeatherSource> weatherSource =
                        weatherSourceResolver.forCountry(trip.getCountryCode());
                if (weatherSource.isEmpty()) {
                    log.info("Skipping trip id={} for polling: no weather provider for country {}",
                            trip.getId(), trip.getCountryCode());
                    continue;
                }

                List<Activity> activities =
                        activityRepository.findByTripIdOrderByStartDateTimeAsc(trip.getId());
                activitiesScanned += activities.size();

                double[] centroid = centroid(activities);
                if (centroid == null) {
                    log.warn("Skipping trip id={} for polling: no outdoor activity location with valid coordinates",
                            trip.getId());
                    continue;
                }

                ZoneId zone = region.get().zone();
                LocalDate tomorrow = LocalDate.now(clock.withZone(zone)).plusDays(1);
                ZonedDateTime from = tomorrow.atStartOfDay(zone);
                ZonedDateTime to = tomorrow.plusDays(1).atStartOfDay(zone);

                if (!weatherSource.get().isRainy(centroid[0], centroid[1], from, to)) {
                    continue;
                }

                if (trip.getOwner() == null) {
                    log.warn("Skipping trip id={} for polling: owner is missing", trip.getId());
                    continue;
                }

                suggestionsCreated += suggestionService.replanForSystem(
                        trip.getId(), trip.getOwner().getId(), null,
                        TriggerSource.POLL_TOMORROW).size();
            } catch (RuntimeException e) {
                log.warn("24h poll failed for trip id={}: {}: {}",
                        trip.getId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }

        log.info("poll_24h trips_scanned={} activities_scanned={} suggestions_created={}",
                tripsScanned, activitiesScanned, suggestionsCreated);
    }

    /**
     * 2 h pass: activities starting in 90–150 minutes. Activities are collected
     * per trip first, and replanning then runs once per distinct trip.
     */
    @Scheduled(fixedDelayString = "${app.polling.activity-delay-ms:600000}",
            initialDelayString = "${app.polling.initial-delay-ms:60000}")
    public void pollUpcomingActivities() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime from = now.plusMinutes(90);
        ZonedDateTime to = now.plusMinutes(150);

        List<Activity> activities = activityRepository.findByStartDateTimeBetween(from, to);

        int activitiesScanned = 0;
        Set<String> tripIds = new LinkedHashSet<>();

        for (Activity activity : activities) {
            activitiesScanned++;
            try {
                List<Location> locations = activity.getLocations();
                if (locations == null || locations.isEmpty()) {
                    log.warn("Skipping activity id={} for polling: no locations", activity.getId());
                    continue;
                }

                Location outdoor = locations.stream()
                        .filter(Location::isOutdoor)
                        .filter(candidate -> candidate.getLatitude() != null
                                && candidate.getLongitude() != null)
                        .findFirst()
                        .orElse(null);
                if (outdoor == null) {
                    log.info("Skipping activity id={} for polling: no outdoor stop",
                            activity.getId());
                    continue;
                }

                Double latitude = outdoor.getLatitude();
                Double longitude = outdoor.getLongitude();

                ZonedDateTime start = activity.getStartDateTime();
                ZonedDateTime end = activity.getEndDateTime();
                ZonedDateTime windowFrom = start != null ? start : now;
                ZonedDateTime windowTo = end != null
                        ? end
                        : windowFrom.plusHours(2);

                Optional<WeatherSource> weatherSource =
                        weatherSourceResolver.forPoint(latitude, longitude);
                if (weatherSource.isEmpty()) {
                    log.info("Skipping activity id={} for polling: no weather provider covers this point",
                            activity.getId());
                    continue;
                }

                if (!weatherSource.get().isRainy(latitude, longitude, windowFrom, windowTo)) {
                    continue;
                }

                if (activity.getTrip() != null) {
                    tripIds.add(activity.getTrip().getId());
                }
            } catch (RuntimeException e) {
                log.warn("2h poll failed for activity id={}: {}: {}",
                        activity.getId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }

        int suggestionsCreated = 0;
        for (String tripId : tripIds) {
            try {
                Trip trip = tripRepository.findById(tripId).orElse(null);
                if (trip == null) {
                    log.warn("Skipping trip id={} for polling: trip not found", tripId);
                    continue;
                }
                if (trip.getOwner() == null) {
                    log.warn("Skipping trip id={} for polling: owner is missing", tripId);
                    continue;
                }

                suggestionsCreated += suggestionService.replanForSystem(
                        tripId, trip.getOwner().getId(), now, TriggerSource.POLL_IMMINENT).size();
            } catch (RuntimeException e) {
                log.warn("2h poll failed for trip id={}: {}: {}",
                        tripId, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        log.info("poll_2h activities_scanned={} trips={} suggestions_created={}",
                activitiesScanned, tripIds.size(), suggestionsCreated);
    }

    /** Mean coordinates over the outdoor locations that have a usable point. */
    private static double[] centroid(List<Activity> activities) {
        double latitudeSum = 0;
        double longitudeSum = 0;
        int count = 0;

        for (Activity activity : activities) {
            if (activity.getLocations() == null) {
                continue;
            }
            for (Location location : activity.getLocations()) {
                if (!location.isOutdoor()) {
                    continue;
                }
                Double latitude = location.getLatitude();
                Double longitude = location.getLongitude();
                if (latitude != null && longitude != null) {
                    latitudeSum += latitude;
                    longitudeSum += longitude;
                    count++;
                }
            }
        }

        return count == 0 ? null : new double[] {latitudeSum / count, longitudeSum / count};
    }
}
