package com.smu.cs203_g1_1.itinerary_planner.routing;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.GlobalExceptionHandler;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.routing.dto.RouteResponse;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RoutingUnavailableTest {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private TripService tripService;

    @Mock
    private OneMapClient oneMapClient;

    private RoutingService routingService;
    private Trip trip;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(
            activityRepository, tripService, oneMapClient, new RegionRegistry());

        User owner = new User("clerk-owner", "Owner");
        owner.setId("User0001");
        trip = new Trip("Weekend", LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3), owner);
        trip.setId("Trip0001");
    }

    @Test
    void singaporeTripsCanUseRouting() {
        when(tripService.getTripForMember("Trip0001", "User0001")).thenReturn(trip);

        Location fromLocation = location("From0001", 1.300, 103.850);
        Location toLocation = location("To000001", 1.310, 103.860);
        Activity from = activity("ActFrom1", fromLocation);
        Activity to = activity("ActTo001", toLocation);

        when(activityRepository.findByIdAndTripId("ActFrom1", "Trip0001"))
            .thenReturn(Optional.of(from));
        when(activityRepository.findByIdAndTripId("ActTo001", "Trip0001"))
            .thenReturn(Optional.of(to));

        RouteResponse response = assertDoesNotThrow(() -> routingService.getRoute(
            "User0001", "Trip0001", "ActFrom1", "ActTo001",
            RouteMode.WALKING, null));

        assertEquals("ActFrom1", response.fromActivityId());
        verify(oneMapClient).getRoute(
            anyDouble(), anyDouble(), anyDouble(), anyDouble(),
            any(RouteMode.class), any(), any());
    }

    @Test
    void countryWithoutARegionFailsBeforeRouting() {
        trip.setCountryCode("jp");
        when(tripService.getTripForMember("Trip0001", "User0001")).thenReturn(trip);

        RoutingUnavailableException error = assertThrows(
            RoutingUnavailableException.class,
            () -> routingService.getRoute(
                "User0001", "Trip0001", "ActFrom1", "ActTo001",
                RouteMode.WALKING, null));

        assertEquals("jp", error.getCountryCode());
        assertEquals("Routing is not available for country jp", error.getMessage());
        verifyNoInteractions(activityRepository, oneMapClient);
    }

    @Test
    void unknownCountryFailsBeforeRouting() {
        trip.setCountryCode("zz");
        when(tripService.getTripForMember("Trip0001", "User0001")).thenReturn(trip);

        RoutingUnavailableException error = assertThrows(
            RoutingUnavailableException.class,
            () -> routingService.getRoute(
                "User0001", "Trip0001", "ActFrom1", "ActTo001",
                RouteMode.WALKING, null));

        assertEquals("zz", error.getCountryCode());
        verifyNoInteractions(activityRepository, oneMapClient);
    }

    @Test
    void handlerMapsUnavailableRoutingTo422() {
        RoutingUnavailableException exception = new RoutingUnavailableException("jp");

        ResponseEntity<Map<String, String>> response =
            new GlobalExceptionHandler().handleRoutingUnavailable(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("ROUTING_UNAVAILABLE", response.getBody().get("code"));
        assertEquals("Routing is not available for country jp",
            response.getBody().get("message"));
    }

    private static Location location(String id, double latitude, double longitude) {
        Location location = new Location("Stop " + id, "culture",
            latitude, longitude, false);
        location.setId(id);
        return location;
    }

    private static Activity activity(String id, Location location) {
        Activity activity = new Activity("Activity " + id,
            new ArrayList<>(List.of(location)),
            ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, SINGAPORE),
            ZonedDateTime.of(2026, 11, 2, 11, 0, 0, 0, SINGAPORE),
            "PLANNED");
        activity.setId(id);
        return activity;
    }
}
