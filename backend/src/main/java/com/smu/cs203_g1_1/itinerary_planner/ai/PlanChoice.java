package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The model's re-ranking of the closed candidate set.
 *
 * <p>The response is deliberately loose here: unknown fields are ignored and
 * entries may be null or incomplete. {@link PlanChoiceValidator} is the only
 * thing allowed to decide whether any of it is usable.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanChoice(
        List<RankedChoice> ranking,
        @JsonProperty("no_suitable_option") boolean noSuitableOption) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankedChoice(String id, int rank, double score, String reason,
                               String description) {
    }
}
