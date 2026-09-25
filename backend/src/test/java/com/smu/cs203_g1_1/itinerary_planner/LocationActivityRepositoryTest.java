package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@DataJpaTest
class LocationActivityRepositoryTest {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createLocationAndActivity() {

        // Create and save User
        User user = new User(
            "test-user-location",
            "Location Test User"
        );

        user = userRepository.save(user);

        // Create and save Trip
        Trip trip = new Trip(
            "Location Activity Test Trip",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5),
            user
        );

        trip.addMember(user);
        trip = tripRepository.save(trip);

        // Create and save Location
        Location location = new Location(
            "Marina Bay Sands",
            "Attraction",
            1.2834,
            103.8607,
            true
        );

        location = locationRepository.save(location);

        // Create and save Activity
        ZonedDateTime start = ZonedDateTime.now();
        ZonedDateTime end = start.plusHours(2);

        Activity activity = new Activity(
            "Visit Marina Bay Sands",
            List.of(location),
            start,
            end,
            "PLANNED"
        );

        activity.setTrip(trip);
        activity = activityRepository.save(activity);

        // Verify Location was persisted
        assertNotNull(location.getId());

        // Verify Activity was persisted
        assertNotNull(activity.getId());

        // Verify Activity -> Location relationship
        assertNotNull(activity.getLocations());
        assertEquals(location.getId(), activity.getLocations().get(0).getId());

        // Verify Activity -> Trip relationship
        assertNotNull(activity.getTrip());
        assertEquals(trip.getId(), activity.getTrip().getId());
    }
}