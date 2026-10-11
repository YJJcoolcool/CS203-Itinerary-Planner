package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@DataJpaTest
@Import(TripService.class)
class TripMembershipTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User member;
    private User outsider;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = saveUser("OwnerUs1", "clerk-owner");
        member = saveUser("MemberU1", "clerk-member");
        outsider = saveUser("Outsidr1", "clerk-outsider");

        trip = tripService.createTrip("Japan", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), owner.getId());
        tripService.addMember(owner.getId(), trip.getId(), member.getId());
    }

    @Test
    void ownerCanAccessTrip() {
        assertEquals(trip.getId(), tripService.getTripForMember(trip.getId(), owner.getId()).getId());
    }

    @Test
    void memberCanAccessTrip() {
        assertEquals(trip.getId(), tripService.getTripForMember(trip.getId(), member.getId()).getId());
    }

    @Test
    void nonMemberIsForbidden() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tripService.getTripForMember(trip.getId(), outsider.getId()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void nonMemberCannotDeleteTrip() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tripService.deleteTrip(outsider.getId(), trip.getId()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void missingTripIsNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tripService.getTripForMember("NoSuchId", owner.getId()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void ownerAndMemberSeeTheTripInTheirList() {
        assertEquals(1, tripService.getTripsForUser(owner.getId()).size());
        assertEquals(1, tripService.getTripsForUser(member.getId()).size());
    }

    @Test
    void nonMemberDoesNotSeeTheTripInTheirList() {
        assertTrue(tripService.getTripsForUser(outsider.getId()).isEmpty());
    }

    private User saveUser(String id, String clerkUserId) {
        User user = new User(clerkUserId, clerkUserId);
        user.setId(id);
        return userRepository.save(user);
    }
}