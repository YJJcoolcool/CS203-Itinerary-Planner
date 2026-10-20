package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns user and trip preferences, including the group summary.
 *
 * <p>Visibility: a user's structured preference is readable by any signed-in
 * user (the controller omits free-text notes for other users). Trip preferences
 * are visible to members and writable by owners/editors.</p>
 */
@Service
public class PreferenceService {

    private static final Set<String> INDOOR_PREFERENCES = Set.of("INDOOR", "OUTDOOR", "ANY");

    private final UserPreferenceRepository userPreferences;
    private final TripPreferenceRepository tripPreferences;
    private final TripService tripService;
    private final PreferenceSummaryGenerator summaryGenerator;

    public PreferenceService(UserPreferenceRepository userPreferences,
                             TripPreferenceRepository tripPreferences,
                             TripService tripService,
                             PreferenceSummaryGenerator summaryGenerator) {
        this.userPreferences = userPreferences;
        this.tripPreferences = tripPreferences;
        this.tripService = tripService;
        this.summaryGenerator = summaryGenerator;
    }

    @Transactional
    public UserPreference getOrCreateUser(String userId) {
        return userPreferences.findById(userId)
            .orElseGet(() -> userPreferences.save(new UserPreference(userId)));
    }

    @Transactional(readOnly = true)
    public UserPreference readUser(String userId) {
        return userPreferences.findById(userId).orElseGet(() -> new UserPreference(userId));
    }

    @Transactional
    public UserPreference updateUser(String userId,
                                     List<String> tags,
                                     BigDecimal maxWalkKm,
                                     String dietary,
                                     String indoorPreference,
                                     String notes) {
        validateIndoorPreference(indoorPreference);

        UserPreference preference = getOrCreateUser(userId);
        preference.setTags(tags);
        preference.setMaxWalkKm(maxWalkKm);
        preference.setDietary(dietary);
        preference.setIndoorPreference(indoorPreference);
        preference.setNotes(notes);
        preference.setSummaryStale(true);
        preference.touch();

        UserPreference saved = userPreferences.save(preference);
        markTripsSummaryStale(userId);

        return saved;
    }

    /**
     * Marks the group summary of every trip the user belongs to as stale.
     *
     * <p>Only existing {@code trip_preference} rows are touched: a trip that has
     * never had a group preference has no cached summary to invalidate.</p>
     */
    private void markTripsSummaryStale(String userId) {
        for (Trip trip : tripService.getTripsForUser(userId)) {
            tripPreferences.findById(trip.getId()).ifPresent(tripPreference -> {
                tripPreference.touch(userId);
                tripPreferences.save(tripPreference);
            });
        }
    }

    @Transactional
    public TripPreference getOrCreateTrip(String tripId, String userId) {
        tripService.getTripForMember(tripId, userId);

        return tripPreferences.findById(tripId)
            .orElseGet(() -> tripPreferences.save(new TripPreference(tripId)));
    }

    @Transactional
    public TripPreference updateTrip(String tripId,
                                     String userId,
                                     List<String> goals,
                                     List<String> tags) {
        Trip trip = tripService.requireTripRole(tripId, userId, TripRole.EDITOR);

        TripPreference preference = tripPreferences.findById(tripId)
            .orElseGet(() -> new TripPreference(trip.getId()));

        preference.setGoals(goals);
        preference.setTags(tags);
        preference.touch(userId);

        return tripPreferences.save(preference);
    }

    /**
     * Regenerates the group summary from every member's preference plus the
     * trip goal, falling back to a deterministic sentence when the model is
     * unavailable.
     */
    @Transactional
    public TripPreference summariseTrip(String tripId, String userId) {
        Trip trip = tripService.requireTripRole(tripId, userId, TripRole.EDITOR);

        TripPreference preference = tripPreferences.findById(tripId)
            .orElseGet(() -> new TripPreference(trip.getId()));

        List<UserPreference> members = trip.getUsers().stream()
            .map(member -> userPreferences.findById(member.getId()).orElse(null))
            .filter(Objects::nonNull)
            .toList();

        String summary = summaryGenerator.summariseGroup(members, preference)
            .orElseGet(() -> fallbackSummary(members, preference));

        preference.setSummary(summary);
        preference.touch(userId);
        preference.setSummaryStale(false);

        return tripPreferences.save(preference);
    }

    /** Regenerates one user's summary; used by the personal profile surface. */
    @Transactional
    public UserPreference summariseUser(String userId) {
        UserPreference preference = getOrCreateUser(userId);

        String summary = summaryGenerator.summariseUser(preference)
            .orElseGet(() -> fallbackUserSummary(preference));

        preference.setSummary(summary);
        preference.setSummaryStale(false);
        preference.touch();

        return userPreferences.save(preference);
    }

    static String fallbackSummary(List<UserPreference> members, TripPreference trip) {
        Set<String> tags = new TreeSet<>();

        if (members != null) {
            members.forEach(member -> tags.addAll(member.getTags()));
        }
        tags.addAll(trip.getTags());

        String goal = trip.getGoals().isEmpty()
            ? "no specific goal"
            : String.join(", ", trip.getGoals());

        String interests = tags.isEmpty() ? "no listed interests" : String.join(", ", tags);

        return "Group goal: " + goal + ". Shared interests: " + interests + ".";
    }

    static String fallbackUserSummary(UserPreference preference) {
        String tags = preference.getTags().isEmpty()
            ? "no listed interests"
            : String.join(", ", preference.getTags());

        return "Interests: " + tags + ".";
    }

    private static void validateIndoorPreference(String indoorPreference) {
        if (indoorPreference == null || indoorPreference.isBlank()) {
            return;
        }

        if (!INDOOR_PREFERENCES.contains(indoorPreference)) {
            throw new IllegalArgumentException(
                "indoorPreference must be one of " + INDOOR_PREFERENCES);
        }
    }
}
