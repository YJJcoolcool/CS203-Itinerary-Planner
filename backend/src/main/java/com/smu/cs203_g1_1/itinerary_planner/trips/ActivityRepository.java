package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository
        extends JpaRepository<Activity, String> {

    /*
     * Get every activity belonging to a particular trip,
     * ordered by starting time.
     */
    List<Activity> findByTripIdOrderByStartDateTimeAsc(String tripId);


    /*
     * Get a specific activity, but only if it belongs
     * to the specified trip.
     */
    Optional<Activity> findByIdAndTripId(
            String activityId,
            String tripId);
}