package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.Instant;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;

/**
 * API view of a suggestion. Deliberately omits the ranking scores and the AI
 * internals; the client only needs the swap and its human-readable rationale.
 *
 * <p>{@code aiPick} and the OSM identity on {@link LocationRef} exist so the
 * evaluation harness can measure AI adoption and OSM verification from the
 * outside instead of reading logs.</p>
 */
public record SuggestionResponse(
        String id,
        String tripId,
        String activityId,
        String activityName,
        SuggestionStatus status,
        TriggerSource triggerSource,
        LocationRef currentLocation,
        LocationRef proposedLocation,
        String reason,
        String explanation,
        /** True when the chooser's pick was used; false means the deterministic fallback. */
        boolean aiPick,
        Instant createdAt,
        Instant respondedAt) {

    public record LocationRef(String id, String name, String category, String indoorClass,
                              double latitude, double longitude,
                              String osmType, Long osmId) {
    }

    public static SuggestionResponse from(Suggestion suggestion) {
        Location current = suggestion.getCurrentLocation();
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getTrip().getId(),
                suggestion.getActivity().getId(),
                suggestion.getActivity().getName(),
                suggestion.getStatus(),
                suggestion.getTriggerSource(),
                current == null ? null : ref(current),
                ref(suggestion.getProposedLocation()),
                suggestion.getReason(),
                suggestion.getExplanation(),
                suggestion.getAiChoiceId() != null,
                suggestion.getCreatedAt(),
                suggestion.getRespondedAt());
    }

    private static LocationRef ref(Location location) {
        return new LocationRef(
                location.getId(),
                location.getName(),
                location.getCategory(),
                location.getIndoorClass(),
                location.getLatitude(),
                location.getLongitude(),
                location.getOsmType(),
                location.getOsmId());
    }
}
