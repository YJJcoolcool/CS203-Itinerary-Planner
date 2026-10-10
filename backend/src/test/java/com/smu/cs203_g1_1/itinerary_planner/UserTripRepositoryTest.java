package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@DataJpaTest
class UserTripRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Test
    void createUserAndTrip() {

        // Create and save User
        User user = new User(
            "test-user-123",
            "Test User"
        );

        user = userRepository.save(user);

        // Create Trip owned by User
        Trip trip = new Trip(
            "Singapore Trip",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5),
            user
        );

        // Add User as a member (the owner always holds the OWNER role)
        trip.addMember(user, TripRole.OWNER);

        // Save Trip
        trip = tripRepository.save(trip);

        // Verify User was persisted
        assertNotNull(user.getId());

        // Verify Trip was persisted
        assertNotNull(trip.getId());

        // Verify Trip fields
        assertEquals("Singapore Trip", trip.getName());

        // Verify ownership relationship
        assertNotNull(trip.getOwner());
        assertEquals(user.getId(), trip.getOwner().getId());

        // Verify membership relationship
        assertTrue(trip.findMember(user).isPresent());
        assertEquals(TripRole.OWNER, trip.findMember(user).get().getRole());
    }
}