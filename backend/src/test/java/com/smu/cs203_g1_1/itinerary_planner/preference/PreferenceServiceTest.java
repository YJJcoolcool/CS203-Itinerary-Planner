package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        public Optional<String> summariseUser(UserPreference preference, TravelHistory history) {
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
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of());

        UserPreference updated = service.updateUser(
            "user0001", List.of("art", "food"), new BigDecimal("1.50"), "vegetarian", "ANY", "quiet places");

        assertEquals(List.of("art", "food"), updated.getTags());
        assertEquals("vegetarian", updated.getDietary());
        assertTrue(updated.isSummaryStale());
    }

    @Test
    void updateUserMarksAnExistingTripSummaryStaleWithoutClearingIt() {
        TripPreference existing = new TripPreference("trip0001");
        existing.setSummary("Group likes museums.");
        existing.setSummaryStale(false);

        when(userPreferences.findById("user0001")).thenReturn(Optional.empty());
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of(trip));
        when(trip.getId()).thenReturn("trip0001");
        when(tripPreferences.findById("trip0001")).thenReturn(Optional.of(existing));
        when(tripPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUser(
            "user0001", List.of("art"), new BigDecimal("1.50"), "vegetarian", "ANY", null);

        assertTrue(existing.isSummaryStale());
        assertEquals("Group likes museums.", existing.getSummary());
        verify(tripPreferences).save(existing);
    }

    @Test
    void updateUserDoesNotCreateATripPreferenceThatDoesNotExist() {
        when(userPreferences.findById("user0001")).thenReturn(Optional.empty());
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of(trip));
        when(trip.getId()).thenReturn("trip0001");
        when(tripPreferences.findById("trip0001")).thenReturn(Optional.empty());

        service.updateUser(
            "user0001", List.of("art"), BigDecimal.ONE, null, "ANY", null);

        verify(tripPreferences, never()).save(any());
    }

    @Test
    void updateTripGoalsStillMarksTheSummaryStale() {
        TripPreference existing = new TripPreference("trip0001");
        existing.setSummary("Old summary.");
        existing.setSummaryStale(false);

        when(tripService.requireTripRole("trip0001", "user0001", TripRole.EDITOR)).thenReturn(trip);
        when(tripPreferences.findById("trip0001")).thenReturn(Optional.of(existing));
        when(tripPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TripPreference updated = service.updateTrip(
            "trip0001", "user0001", List.of("MUSEUMS"), List.of("art"));

        assertTrue(updated.isSummaryStale());
        assertEquals("Old summary.", updated.getSummary());
    }

    @Test
    void rejectsAnUnknownIndoorPreferenceBeforeTouchingTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), null, null, "SOMETIMES", null));

        verifyNoInteractions(userPreferences);
    }

    @Test
    void rejectsFieldsLongerThanTheirColumnsBeforeTouchingTheDatabase() {
        String longTag = "x".repeat(PreferenceService.MAX_LIST_CHARS + 1);

        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(longTag), null, null, "ANY", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), null, "d".repeat(PreferenceService.MAX_DIETARY_CHARS + 1), "ANY", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), null, null, "ANY", "n".repeat(PreferenceService.MAX_TEXT_CHARS + 1)));

        verifyNoInteractions(userPreferences);
    }

    @Test
    void tagLimitCountsTheCommaJoinedColumnNotEachTag() {
        // 26 tags of 9 chars + 25 commas = 259 chars: each tag is short, the column is not.
        List<String> tags = java.util.stream.IntStream.range(0, 26)
            .mapToObj(i -> String.format("tag%06d", i))
            .toList();

        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", tags, null, null, "ANY", null));
    }

    @Test
    void maxWalkMustFitNumericFourTwoAfterRounding() {
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), new BigDecimal("-0.5"), null, "ANY", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), new BigDecimal("100"), null, "ANY", null));
        // Rounds to 100.00, which numeric(4,2) cannot hold.
        assertThrows(IllegalArgumentException.class, () -> service.updateUser(
            "user0001", List.of(), new BigDecimal("99.995"), null, "ANY", null));

        verifyNoInteractions(userPreferences);
    }

    @Test
    void acceptsTheLargestWalkThatFits() {
        when(userPreferences.findById("user0001")).thenReturn(Optional.empty());
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of());

        UserPreference updated = service.updateUser(
            "user0001", List.of(), new BigDecimal("99.99"), null, "ANY", null);

        assertEquals(new BigDecimal("99.99"), updated.getMaxWalkKm());
    }

    @Test
    void updateTripRejectsGoalsLongerThanTheColumn() {
        when(tripService.requireTripRole("trip0001", "user0001", TripRole.EDITOR)).thenReturn(trip);

        assertThrows(IllegalArgumentException.class, () -> service.updateTrip(
            "trip0001", "user0001", List.of("g".repeat(PreferenceService.MAX_LIST_CHARS + 1)), List.of()));

        verify(tripPreferences, never()).save(any());
    }

    @Test
    void anOverlongModelSummaryIsTruncatedToFitTheColumn() {
        PreferenceSummaryGenerator verbose = new PreferenceSummaryGenerator() {
            @Override
            public Optional<String> summariseGroup(List<UserPreference> members, TripPreference trip) {
                return Optional.of("s".repeat(PreferenceService.MAX_TEXT_CHARS + 500));
            }

            @Override
            public Optional<String> summariseUser(UserPreference preference, TravelHistory history) {
                return Optional.empty();
            }
        };
        service = new PreferenceService(userPreferences, tripPreferences, tripService, verbose);

        when(tripService.requireTripRole("trip0001", "user0001", TripRole.EDITOR)).thenReturn(trip);
        when(trip.getUsers()).thenReturn(List.of());
        when(tripPreferences.findById("trip0001")).thenReturn(Optional.of(new TripPreference("trip0001")));
        when(tripPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TripPreference summarised = service.summariseTrip("trip0001", "user0001");

        assertEquals(PreferenceService.MAX_TEXT_CHARS, summarised.getSummary().length());
        assertTrue(summarised.getSummary().endsWith("…"));
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

    @Test
    void travelHistoryCountsCategoriesAndSkipsRepeatedPlaces() {
        Location museum = location("loc00001", "National Gallery", "Museum");
        Location park = location("loc00002", "Botanic Gardens", "park");
        Location secondMuseum = location("loc00003", "ArtScience Museum", "museum");

        when(tripService.getTripsForUser("user0001")).thenReturn(List.of(trip));
        when(trip.getItinerary()).thenReturn(List.of(
            activity(museum, park),
            activity(secondMuseum, museum)));

        TravelHistory history = service.travelHistory("user0001");

        assertEquals(1, history.tripCount());
        // "Museum" and "museum" count together; museum (2) ranks above park (1).
        assertEquals(List.of("museum", "park"), history.topCategories());
        // National Gallery appears twice but is listed once.
        assertEquals(List.of("National Gallery", "Botanic Gardens", "ArtScience Museum"), history.places());
    }

    @Test
    void summariseUserPassesTripHistoryToTheModel() {
        TravelHistory[] seen = new TravelHistory[1];
        PreferenceSummaryGenerator recording = new PreferenceSummaryGenerator() {
            @Override
            public Optional<String> summariseGroup(List<UserPreference> members, TripPreference tripPreference) {
                return Optional.empty();
            }

            @Override
            public Optional<String> summariseUser(UserPreference preference, TravelHistory history) {
                seen[0] = history;
                return Optional.of("Loves museums.");
            }
        };
        service = new PreferenceService(userPreferences, tripPreferences, tripService, recording);

        when(userPreferences.findById("user0001")).thenReturn(Optional.of(preferenceOf("user0001", List.of("art"))));
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of(trip));
        when(trip.getItinerary()).thenReturn(List.of(activity(location("loc00001", "National Gallery", "museum"))));

        UserPreference summarised = service.summariseUser("user0001");

        assertEquals(List.of("National Gallery"), seen[0].places());
        assertEquals("Loves museums.", summarised.getSummary());
        assertFalse(summarised.isSummaryStale());
    }

    @Test
    void summariseUserFallbackMentionsWhatTheyPlanMost() {
        when(userPreferences.findById("user0001")).thenReturn(Optional.of(preferenceOf("user0001", List.of("food"))));
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of(trip));
        when(trip.getItinerary()).thenReturn(List.of(activity(location("loc00001", "Maxwell Food Centre", "hawker"))));

        UserPreference summarised = service.summariseUser("user0001");

        assertEquals("Interests: food. Often plans: hawker.", summarised.getSummary());
    }

    @Test
    void summariseUserWithNoTripsStillWorks() {
        when(userPreferences.findById("user0001")).thenReturn(Optional.of(preferenceOf("user0001", List.of())));
        when(userPreferences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripService.getTripsForUser("user0001")).thenReturn(List.of());

        UserPreference summarised = service.summariseUser("user0001");

        assertEquals("Interests: no listed interests.", summarised.getSummary());
    }

    private static Location location(String id, String name, String category) {
        Location location = new Location(name, category, 1.29, 103.85, null);
        location.setId(id);

        return location;
    }

    private static Activity activity(Location... locations) {
        Activity activity = new Activity();
        activity.setLocations(List.of(locations));

        return activity;
    }

    private static UserPreference preferenceOf(String userId, List<String> tags) {
        UserPreference preference = new UserPreference(userId);
        preference.setTags(tags);

        return preference;
    }
}
