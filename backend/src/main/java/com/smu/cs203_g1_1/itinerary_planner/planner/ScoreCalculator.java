package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Deterministic, code-side ranking of candidate places.
 *
 * <p>These scores only order the shortlist before any model call; the LLM never
 * sees them and the UI never shows them. The formula is pure, so identical
 * inputs always produce an identical score and the caller controls tie-breaks.</p>
 */
public final class ScoreCalculator {

    public static final double DISTANCE_MAX_KM = 3.0;
    public static final double COST_PENALTY_MAX = 0.3;
    public static final double UNKNOWN_HOURS_PENALTY = 0.25;

    public static final double SIMILARITY_WEIGHT = 0.5;
    public static final double PREFERENCE_WEIGHT = 0.3;
    public static final double QUALITY_WEIGHT = 0.2;

    private static final double COST_DIVISOR = 100.0;

    public double score(String candidateGroup, String droppedGroup, Set<String> interests,
                        double distanceKm, BigDecimal estCost,
                        boolean unknownHours, double quality) {
        double similarity = CategorySimilarity.similarity(candidateGroup, droppedGroup);
        double preferenceMatch = interests != null && interests.contains(candidateGroup) ? 1.0 : 0.0;
        double qualityClamped = clamp(quality, 0.0, 1.0);

        double score = SIMILARITY_WEIGHT * similarity
                + PREFERENCE_WEIGHT * preferenceMatch
                + QUALITY_WEIGHT * qualityClamped
                - distancePenalty(distanceKm)
                - costPenalty(estCost)
                - (unknownHours ? UNKNOWN_HOURS_PENALTY : 0.0);

        return Double.isNaN(score) ? 0.0 : score;
    }

    static double distancePenalty(double km) {
        if (Double.isNaN(km)) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, km) / DISTANCE_MAX_KM);
    }

    static double costPenalty(BigDecimal cost) {
        if (cost == null) {
            return 0.0;
        }
        double amount = cost.max(BigDecimal.ZERO).doubleValue();
        if (Double.isNaN(amount)) {
            return 0.0;
        }
        return Math.min(COST_PENALTY_MAX, amount / COST_DIVISOR);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
