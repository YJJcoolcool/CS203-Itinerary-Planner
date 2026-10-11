package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripController;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripController.TripRequest;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@ExtendWith(MockitoExtension.class)
class TripControllerTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 5);

    @Mock
    private TripService tripService;

    @Mock
    private UserService userService;

    @Mock
    private Jwt jwt;

    private TripController controller;

    @BeforeEach
    void setUp() {
        controller = new TripController(tripService, userService);
    }

    @Test
    void createTripUsesLoggedInUserAsOwner() {
        when(userService.getCurrentUserId(jwt)).thenReturn("OwnerUs1");

        controller.createTrip(jwt, new TripRequest("Japan", START, END));

        verify(tripService).createTrip("Japan", START, END, "OwnerUs1");
    }

    @Test
    void getTripsReturnsOnlyTheLoggedInUsersTrips() {
        User user = new User("clerk-owner", "Owner");
        user.setId("OwnerUs1");
        Trip trip = new Trip("Japan", START, END, user);
        when(userService.findCurrentUser(jwt)).thenReturn(Optional.of(user));
        when(tripService.getTripsForUser("OwnerUs1")).thenReturn(List.of(trip));

        assertEquals(List.of(trip), controller.getTrips(jwt));
    }

    @Test
    void getTripsIsEmptyForUserWithoutProfile() {
        when(userService.findCurrentUser(jwt)).thenReturn(Optional.empty());

        assertTrue(controller.getTrips(jwt).isEmpty());
        verify(tripService, never()).getTripsForUser(org.mockito.ArgumentMatchers.anyString());
    }
}