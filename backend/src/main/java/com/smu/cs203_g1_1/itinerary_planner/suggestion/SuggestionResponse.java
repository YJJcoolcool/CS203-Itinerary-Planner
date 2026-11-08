package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        /** The option actually applied; null until accepted (CGT-169). */
        LocationRef acceptedLocation,
        String reason,
        String explanation,
        List<Option> options,
        /** True when the chooser's pick was used; false means the deterministic fallback. */
        boolean aiPick,
        Instant createdAt,
        Instant respondedAt) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public record LocationRef(String id, String name, String category, String indoorClass,
                              double latitude, double longitude,
                              String osmType, Long osmId) {
    }

    public record Option(LocationRef location, int rank, String reason,
                         String description, boolean aiPick) {
    }

    public static SuggestionResponse from(Suggestion suggestion) {
        Location current = suggestion.getCurrentLocation();
        Location accepted = suggestion.getAcceptedLocation();
        List<Option> options = options(suggestion);
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getTrip().getId(),
                suggestion.getActivity().getId(),
                suggestion.getActivity().getName(),
                suggestion.getStatus(),
                suggestion.getTriggerSource(),
                current == null ? null : ref(current),
                ref(suggestion.getProposedLocation()),
                accepted == null ? null : ref(accepted),
                suggestion.getReason(),
                suggestion.getExplanation(),
                options,
                options.get(0).aiPick(),
                suggestion.getCreatedAt(),
                suggestion.getRespondedAt());
    }

    /**
     * Parses the stored option snapshot, synthesising one option from the
     * legacy proposed location when the row predates the snapshot (or it is
     * malformed).
     */
    private static List<Option> options(Suggestion suggestion) {
        String json = suggestion.getOptionsJson();
        if (json != null && !json.isBlank()) {
            try {
                List<Option> parsed = OBJECT_MAPPER.readValue(
                        json, new TypeReference<List<Option>>() {
                        });
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (JsonProcessingException e) {
                // Fall through to the legacy single-option synthesis.
            }
        }

        Location proposed = suggestion.getProposedLocation();
        return List.of(new Option(
                ref(proposed),
                1,
                suggestion.getExplanation(),
                proposed.getName() + " is an indoor alternative.",
                suggestion.getAiChoiceId() != null));
    }

    public static LocationRef ref(Location location) {
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
