package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    private final PreferenceSummaryGenerator noSummaries = new PreferenceSummaryGenerator() {
        @Override
        public Optional<String> summariseGroup(List<UserPreference> members, TripPreference trip) {
            return Optional.empty();
        }

        @Override
        public Optional<String> summariseUser(UserPreference preference) {
            return Optional.empty();
        }
    };

    @Mock
    private UserPreferenceRepository userPreferences;

    @Mock
    private TripPreferenceRepository tripPreferences;

    @Mock
    private TripService tripService;

    @Mock
    private Trip trip;

    @Mock
    private User member;

    private PreferenceService service;

    @BeforeEach
    void setUp() {
        service = new PreferenceService(userPreferences, tripPreferences, tripService, noSummaries);
    }

    @Test
    void updateUserStoresPreferencesAndMarksTheSummaryStale() {
        when(userPreferences.findById("user0001")).thenReturn(Optional.empty());
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreference updated = service.updateUser(
            "user0001", List.of("art", "food"), new BigDecimal("1.50"), "vegetarian", "ANY", "quiet places");

        assertEquals(List.of("art", "food"), updated.getTags());
        assertEquals("vegetarian", updated.getDietary());
        assertTrue(updated.isSummaryStale());
    }

    @Test
    void rejectsAnUnknownIndoorPreferenceBeforeTouchingTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), null, null, "SOMETIMES", null));

        verifyNoInteractions(userPreferences);
    }

    @Test
    void unknownUserReadsAsAnEmptyPreference() {
        when(userPreferences.findById("ghost")).thenReturn(Optional.empty());

        UserPreference preference = service.readUser("ghost");

        assertEquals("ghost", preference.getUserId());
        assertTrue(preference.getTags().isEmpty());
    }

    @Test
    void summariseFallsBackToTagsWhenNoModelIsConfigured() {
        when(tripService.requireTripRole("trip0001", "user0001", TripRole.EDITOR)).thenReturn(trip);
        when(trip.getId()).thenReturn("trip0001");
        when(trip.getUsers()).thenReturn(List.of(member));
        when(member.getId()).thenReturn("user0002");
        when(tripPreferences.findById("trip0001")).thenReturn(Optional.empty());
        when(userPreferences.findById("user0002")).thenReturn(Optional.of(
            preferenceOf("user0002", List.of("art", "food"))));
        when(tripPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TripPreference summarised = service.summariseTrip("trip0001", "user0001");

        assertFalse(summarised.isSummaryStale());
        assertTrue(summarised.getSummary().contains("art"));
        assertTrue(summarised.getSummary().contains("no specific goal"));
    }

    @Test
    void summariseIncludesTheTripGoal() {
        TripPreference existing = new TripPreference("trip0001");
        existing.setGoals(List.of("MUSEUMS", "FOOD"));

        when(tripService.requireTripRole("trip0001", "user0001", TripRole.EDITOR)).thenReturn(trip);
        when(trip.getUsers()).thenReturn(List.of());
        when(tripPreferences.findById("trip0001")).thenReturn(Optional.of(existing));
        when(tripPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TripPreference summarised = service.summariseTrip("trip0001", "user0001");

        assertTrue(summarised.getSummary().contains("MUSEUMS"));
        assertTrue(summarised.getSummary().contains("FOOD"));
    }

    private static UserPreference preferenceOf(String userId, List<String> tags) {
        UserPreference preference = new UserPreference(userId);
        preference.setTags(tags);

        return preference;
    }
}
