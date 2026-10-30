package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChooser;
import com.smu.cs203_g1_1.itinerary_planner.discovery.PlaceDiscoveryService;
import com.smu.cs203_g1_1.itinerary_planner.planner.CandidateFinder;
import com.smu.cs203_g1_1.itinerary_planner.preference.TripPreferenceRepository;
import com.smu.cs203_g1_1.itinerary_planner.preference.UserPreferenceRepository;
import com.smu.cs203_g1_1.itinerary_planner.rag.QueryEmbedder;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceScopeTest {

    private static final String TRIP_ID = "Trip0001";
    private static final String USER_ID = "User0001";
    private static final ZonedDateTime AT = ZonedDateTime.of(
        2026, 11, 2, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock private SuggestionRepository suggestionRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private TripService tripService;
    @Mock private CandidateFinder candidateFinder;
    @Mock private WeatherSource weatherSource;
    @Mock private WeatherSourceResolver weatherSourceResolver;
    @Mock private PlanChooser planChooser;
    @Mock private ObjectProvider<QueryEmbedder> queryEmbedder;
    @Mock private ObjectProvider<PlaceDiscoveryService> discoveryService;
    @Mock private UserPreferenceRepository userPreferences;
    @Mock private TripPreferenceRepository tripPreferences;

    private SuggestionService suggestionService;
    private Trip trip;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AT.toInstant(), ZoneOffset.UTC);
        suggestionService = new SuggestionService(
            suggestionRepository,
            activityRepository,
            tripService,
            candidateFinder,
            weatherSourceResolver,
            planChooser,
            queryEmbedder,
            discoveryService,
            userPreferences,
            tripPreferences,
            clock);

        User owner = new User("clerk-owner", "Owner");
        owner.setId(USER_ID);
        trip = new Trip("Japan", LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 5), owner);
        trip.setId(TRIP_ID);
    }

    @Test
    void countryWithoutAWeatherProviderSkipsReplanning() {
        trip.setCountryCode("jp");
        when(tripService.requireTripRole(TRIP_ID, USER_ID, TripRole.EDITOR))
            .thenReturn(trip);
        when(weatherSourceResolver.forCountry("jp")).thenReturn(Optional.empty());

        List<SuggestionResponse> result = assertDoesNotThrow(
            () -> suggestionService.replan(TRIP_ID, USER_ID, AT));

        assertTrue(result.isEmpty());
        verifyNoInteractions(activityRepository);
        verifyNoInteractions(weatherSource);
    }

    @Test
    void singaporeReplansPastTheWeatherGate() {
        trip.setCountryCode("sg");
        when(tripService.requireTripRole(TRIP_ID, USER_ID, TripRole.EDITOR))
            .thenReturn(trip);
        when(weatherSourceResolver.forCountry("sg"))
            .thenReturn(Optional.of(weatherSource));
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc(TRIP_ID))
            .thenReturn(List.of());

        List<SuggestionResponse> result = assertDoesNotThrow(
            () -> suggestionService.replan(TRIP_ID, USER_ID, AT));

        assertTrue(result.isEmpty());
        verify(activityRepository).findByTripIdOrderByStartDateTimeAsc(TRIP_ID);
    }
}
