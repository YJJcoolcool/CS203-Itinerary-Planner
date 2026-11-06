package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
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

    // Column limits from V7__preferences.sql; checked here so bad input is a 400, not a DB 500.
    static final int MAX_LIST_CHARS = 255;
    static final int MAX_DIETARY_CHARS = 64;
    static final int MAX_TEXT_CHARS = 1024;
    static final BigDecimal MAX_WALK_KM = new BigDecimal("99.99");
    static final int MAX_HISTORY_PLACES = 20;
    static final int MAX_HISTORY_CATEGORIES = 5;

    private static final CsvListConverter CSV = new CsvListConverter();

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
        validateList("tags", tags);
        validateLength("dietary", dietary, MAX_DIETARY_CHARS);
        validateLength("notes", notes, MAX_TEXT_CHARS);
        validateMaxWalk(maxWalkKm);

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
        validateList("goals", goals);
        validateList("tags", tags);

        TripPreference preference = tripPreferences.findById(tripId)
            .orElseGet(() -> new TripPreference(trip.getId()));

        preference.setGoals(goals);
        preference.setTags(tags);
        preference.touch(userId);

        return tripPreferences.save(preference);
    }

    /**
     * Regenerates the group summary from every member's preference and personal
     * summary plus the trip goal, falling back to a deterministic sentence when the model is
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

        preference.setSummary(truncate(summary));
        preference.touch(userId);
        preference.setSummaryStale(false);

        return tripPreferences.save(preference);
    }

    /**
     * Saves the user's own wording of their summary. A blank value
     * clears it. The user chose these words, so the summary counts as current.
     */
    @Transactional
    public UserPreference updateUserSummary(String userId, String summary) {
        validateLength("summary", summary, MAX_TEXT_CHARS);

        UserPreference preference = getOrCreateUser(userId);
        preference.setSummary(summary == null || summary.isBlank() ? null : summary.trim());
        preference.setSummaryStale(false);
        preference.touch();

        UserPreference saved = userPreferences.save(preference);
        markTripsSummaryStale(userId);

        return saved;
    }

    /**
     * Regenerates one user's summary from their stated preferences plus the
     * places in their own trips. Private notes are never included.
     */
    @Transactional
    public UserPreference summariseUser(String userId) {
        UserPreference preference = getOrCreateUser(userId);
        TravelHistory history = travelHistory(userId);

        String summary = summaryGenerator.summariseUser(preference, history)
            .orElseGet(() -> fallbackUserSummary(preference, history));

        preference.setSummary(truncate(summary));
        preference.setSummaryStale(false);
        preference.touch();

        UserPreference saved = userPreferences.save(preference);
        markTripsSummaryStale(userId);

        return saved;
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

    /**
     * Every place across the trips the user is on, de-duplicated, plus the
     * categories they plan most (most frequent first, ties alphabetical).
     */
    TravelHistory travelHistory(String userId) {
        List<Trip> trips = tripService.getTripsForUser(userId);

        Map<String, String> places = new LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();

        for (Trip trip : trips) {
            for (Activity activity : trip.getItinerary()) {
                for (Location location : activity.getLocations()) {
                    String key = location.getId() != null ? location.getId() : location.getName();

                    if (key == null || places.containsKey(key)) {
                        continue;
                    }
                    places.put(key, location.getName());

                    String category = location.getCategory();
                    if (category != null && !category.isBlank()) {
                        categoryCounts.merge(category.trim().toLowerCase(), 1, Integer::sum);
                    }
                }
            }
        }

        List<String> topCategories = categoryCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
            .limit(MAX_HISTORY_CATEGORIES)
            .map(Map.Entry::getKey)
            .toList();

        List<String> placeNames = places.values().stream()
            .limit(MAX_HISTORY_PLACES)
            .toList();

        return new TravelHistory(trips.size(), topCategories, placeNames);
    }

    static String fallbackUserSummary(UserPreference preference, TravelHistory history) {
        String tags = preference.getTags().isEmpty()
            ? "no listed interests"
            : String.join(", ", preference.getTags());

        String summary = "Interests: " + tags + ".";

        if (!history.topCategories().isEmpty()) {
            summary += " Often plans: " + String.join(", ", history.topCategories()) + ".";
        }

        return summary;
    }

    /** Lists are stored as one comma-joined column, so the joined length is what counts. */
    private static void validateList(String field, List<String> values) {
        String joined = CSV.convertToDatabaseColumn(values);

        if (joined != null && joined.length() > MAX_LIST_CHARS) {
            throw new IllegalArgumentException(
                field + " must be at most " + MAX_LIST_CHARS + " characters in total");
        }
    }

    private static void validateLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
    }

    /** numeric(4,2) rounds to 2 dp first, so 99.995 would overflow; compare the rounded value. */
    private static void validateMaxWalk(BigDecimal maxWalkKm) {
        if (maxWalkKm == null) {
            return;
        }

        BigDecimal rounded = maxWalkKm.setScale(2, RoundingMode.HALF_UP);

        if (rounded.signum() < 0 || rounded.compareTo(MAX_WALK_KM) > 0) {
            throw new IllegalArgumentException("maxWalkKm must be between 0 and " + MAX_WALK_KM);
        }
    }

    /** LLM output length is not guaranteed, and the fallback grows with group size. */
    static String truncate(String summary) {
        if (summary == null || summary.length() <= MAX_TEXT_CHARS) {
            return summary;
        }

        return summary.substring(0, MAX_TEXT_CHARS - 1) + "…";
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
