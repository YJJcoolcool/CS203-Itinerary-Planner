package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Everything the chooser is allowed to see for one affected stop.
 *
 * <p>It contains no internal scores, no ranking order, and no raw OSM tags.
 * Candidate fields are whitelisted on purpose.</p>
 */
public record PlanChoiceRequest(
        String triggerReason,
        String activityName,
        String activityCategoryGroup,
        ZonedDateTime arrival,
        Set<String> interests,
        String nextActivityName,
        List<PlanCandidate> candidates) {

    /**
     * One candidate as the model sees it. Ordering in the enclosing list is
     * incidental; the model must judge by fields, not position.
     */
    public record PlanCandidate(
            String id,
            String name,
            String category,
            String categoryGroup,
            double distanceKm,
            String openingStatus,
            boolean indoor,
            BigDecimal estCost) {
    }
}
