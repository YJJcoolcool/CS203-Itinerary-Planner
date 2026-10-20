package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.util.Optional;

/**
 * Chooses among a closed set of already-filtered candidates.
 *
 * <p>Implementations must return {@link Optional#empty()} on any failure and
 * must never invent a candidate. Callers always fall back to their own ranking
 * when the result is empty.</p>
 */
public interface PlanChooser {

    Optional<PlanChoice> choose(PlanChoiceRequest request);
}
