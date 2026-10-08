package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
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

        user.setId("TestUsr1");

        User saved = userRepository.save(user);

        // Verify User creation and that ID matches the 8 alphanumeric format
        assertNotNull(saved.getId());
        assertEquals(8, saved.getId().length());
        assertTrue(saved.getId().matches("[A-Za-z0-9]{8}"));

        // Verify that the user is stored in the database
        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());

        // Create Trip owned by User
        Trip trip = new Trip(
            "Singapore Trip",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5),
            saved
        );

        trip.setId("TestTrp1");

        // Verify Trip creation and that ID matches the 8 alphanumeric format
        assertNotNull(trip.getId());
        assertEquals(8, trip.getId().length());
        assertTrue(trip.getId().matches("[A-Za-z0-9]{8}"));

        // Add User as a member
        trip.addMember(saved);

        // Save Trip
        trip = tripRepository.save(trip);

        // Verify Trip was persisted
        assertNotNull(trip.getId());

        // Verify Trip fields
        assertEquals("Singapore Trip", trip.getName());

        // Verify ownership relationship
        assertNotNull(trip.getOwner());
        assertEquals(saved.getId(), trip.getOwner().getId());

        // Verify membership relationship
        assertTrue(trip.getUsers().contains(saved));
    }
}