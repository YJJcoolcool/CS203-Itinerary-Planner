package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.time.LocalDate;
import java.util.List;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripWeatherOutlookControllerTest {

    private static final String TRIP_ID = "Trip0001";
    private static final String USER_ID = "User0001";

    @Mock
    private WeatherOutlookService weatherOutlookService;

    @Mock
    private TripService tripService;

    @Mock
    private UserService userService;

    @Mock
    private Jwt jwt;

    private TripWeatherOutlookController controller;
    private Trip trip;

    @BeforeEach
    void setUp() {
        controller = new TripWeatherOutlookController(
            weatherOutlookService, tripService, userService);

        User owner = new User("clerk-owner", "Owner");
        owner.setId(USER_ID);
        trip = new Trip("Japan", LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 5), owner);
        trip.setId(TRIP_ID);
    }

    @Test
    void memberCanReadTheOutlook() {
        when(userService.getCurrentUserId(jwt)).thenReturn(USER_ID);
        when(tripService.getTripForMember(TRIP_ID, USER_ID)).thenReturn(trip);
        DailyOutlook day = new DailyOutlook(LocalDate.of(2026, 11, 2),
            true, 1.2, 80, 61);
        when(weatherOutlookService.outlook(trip)).thenReturn(List.of(day));

        List<DailyOutlook> result = controller.outlook(jwt, TRIP_ID);

        assertEquals(List.of(day), result);
    }

    @Test
    void membershipIsEnforced() {
        when(userService.getCurrentUserId(jwt)).thenReturn(USER_ID);
        when(tripService.getTripForMember(TRIP_ID, USER_ID))
            .thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Not a member"));

        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> controller.outlook(jwt, TRIP_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void unresolvedWeatherReturnsEmptyListInsteadOfAnError() {
        when(userService.getCurrentUserId(jwt)).thenReturn(USER_ID);
        when(tripService.getTripForMember(TRIP_ID, USER_ID)).thenReturn(trip);
        when(weatherOutlookService.outlook(trip)).thenReturn(List.of());

        assertTrue(controller.outlook(jwt, TRIP_ID).isEmpty());
    }
}
