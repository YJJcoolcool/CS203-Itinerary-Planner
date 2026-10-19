package com.smu.cs203_g1_1.itinerary_planner.geo;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.smu.cs203_g1_1.itinerary_planner.GlobalExceptionHandler;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@ExtendWith(MockitoExtension.class)
class GeoControllerTest {

    @Mock
    private GeoService geoService;

    @Mock
    private PoiService poiService;

    @Mock
    private TripService tripService;
    
    @Mock
    private UserService userService;
    
    @Mock
    private Jwt jwt;

    private MockMvc mockMvc;
    private PlaceDto place;
    private GeoController controller;

    @BeforeEach
    void setUp() {
        controller = new GeoController(
            geoService,
            poiService,
            tripService,
            userService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        place = new PlaceDto(
            "node", 123L, "Marina Bay Sands",
            "Marina Bay Sands, Singapore", "tourism", "attraction",
            1.2834, 103.8607, null, null, null, 0.8
        );
    }

    @Test
    void blankQueryReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/geo/search").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void outOfRangeLatitudeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/geo/reverse").param("lat", "999").param("lon", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void unknownPoiCategoryReturnsBadRequest() throws Exception {
        when(poiService.findPois(anyDouble(), anyDouble(), anyInt(), eq("spaceship"), anyInt()))
                .thenThrow(new GeoBadRequestException("Unsupported category: spaceship"));

        mockMvc.perform(get("/api/geo/pois")
                        .param("lat", "1").param("lon", "1").param("category", "spaceship"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void validSearchReturnsResultsAndAttribution() throws Exception {
        when(geoService.search("coffee", 2, null, null, false))
                .thenReturn(List.of(place));

        mockMvc.perform(
                get("/api/geo/search")
                        .param("q", "coffee")
                        .param("limit", "2")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].osmType").value("node"))
                .andExpect(jsonPath("$.attribution").value(OsmAttribution.TEXT));

        verify(geoService).search("coffee", 2, null, null, false);
    }

    @Test
    void validReverseReturnsPlace() throws Exception {
        when(geoService.reverse(1.29, 103.85, 18)).thenReturn(place);

        mockMvc.perform(get("/api/geo/reverse").param("lat", "1.29").param("lon", "103.85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.osmType").value("node"))
                .andExpect(jsonPath("$.attribution").value(OsmAttribution.TEXT));
    }

    @Test
    void reverseNotFoundReturns404() throws Exception {
        when(geoService.reverse(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new GeoNotFoundException("No location found for the given coordinates"));

        mockMvc.perform(get("/api/geo/reverse").param("lat", "1.29").param("lon", "103.85"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void reverseUnavailableReturns503() throws Exception {
        when(geoService.reverse(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new GeoUnavailableException("Geocoding service is temporarily unavailable"));

        mockMvc.perform(get("/api/geo/reverse").param("lat", "1.29").param("lon", "103.85"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void searchUsesCountriesFromTrip() {
        User owner = new User("owner", "Owner");
        owner.setId("OwnerUs1");

        Trip trip = new Trip(
                "Japan and Singapore",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 5),
                owner
        );
        trip.setId("Trip1234");
        trip.setCountries(List.of("jp", "sg"));

        when(userService.getCurrentUserId(jwt))
                .thenReturn("OwnerUs1");

        when(tripService.getTripForMember("Trip1234", "OwnerUs1"))
                .thenReturn(trip);

        when(geoService.search(
                "coffee", 5, "jp,sg", null, false
        )).thenReturn(List.of(place));

        controller.search(
                "coffee",
                5,
                null,
                null,
                false,
                "Trip1234",
                jwt
        );

        verify(geoService).search(
                "coffee",
                5,
                "jp,sg",
                null,
                false
        );
    }

    @Test
    void searchWithoutTripUsesProvidedCountryCodes() {
        when(geoService.search(
                "Tokyo", 5, "jp", null, false
        )).thenReturn(List.of(place));

        controller.search(
                "Tokyo",
                5,
                "JP",
                null,
                false,
                null,
                jwt
        );

        verify(geoService).search(
                "Tokyo",
                5,
                "jp",
                null,
                false
        );
    }
}
