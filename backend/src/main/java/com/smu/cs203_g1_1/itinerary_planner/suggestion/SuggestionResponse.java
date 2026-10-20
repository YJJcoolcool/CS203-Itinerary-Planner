package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.Instant;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;

/**
 * API view of a suggestion. Deliberately omits the ranking scores and the AI
 * internals; the client only needs the swap and its human-readable rationale.
 */
public record SuggestionResponse(
        String id,
        String tripId,
        String activityId,
        String activityName,
        SuggestionStatus status,
        LocationRef currentLocation,
        LocationRef proposedLocation,
        String reason,
        String explanation,
        Instant createdAt,
        Instant respondedAt) {

    public record LocationRef(String id, String name, String category,
                              double latitude, double longitude) {
    }

    public static SuggestionResponse from(Suggestion suggestion) {
        Location current = suggestion.getCurrentLocation();
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getTrip().getId(),
                suggestion.getActivity().getId(),
                suggestion.getActivity().getName(),
                suggestion.getStatus(),
                current == null ? null : ref(current),
                ref(suggestion.getProposedLocation()),
                suggestion.getReason(),
                suggestion.getExplanation(),
                suggestion.getCreatedAt(),
                suggestion.getRespondedAt());
    }

    private static LocationRef ref(Location location) {
        return new LocationRef(
                location.getId(),
                location.getName(),
                location.getCategory(),
                location.getLatitude(),
                location.getLongitude());
    }
}
