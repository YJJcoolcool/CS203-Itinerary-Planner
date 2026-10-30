package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository
        extends JpaRepository<Activity, String> {

    /*
     * Get every activity belonging to a particular trip,
     * ordered by starting time.
     */
    @EntityGraph(attributePaths = "locations")
    List<Activity> findByTripIdOrderByStartDateTimeAsc(String tripId);

    /*
     * Activities whose start falls in [from, to], for the proactive poller.
     * Locations are fetched eagerly so the poller can read them outside a
     * transaction without a LazyInitializationException.
     */
    @EntityGraph(attributePaths = "locations")
    List<Activity> findByStartDateTimeBetween(ZonedDateTime from, ZonedDateTime to);


    /*
     * Get a specific activity, but only if it belongs
     * to the specified trip.
     */
    @EntityGraph(attributePaths = "locations")
    Optional<Activity> findByIdAndTripId(
            String activityId,
            String tripId);
}
