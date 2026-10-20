package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Validates a model response against the candidates we actually offered.
 *
 * <p>The model is untrusted. Unknown ids, duplicates, malformed ranks and
 * ungrounded reasons are dropped rather than repaired. Scores are advisory and
 * never used for ordering.</p>
 */
public final class PlanChoiceValidator {

    private PlanChoiceValidator() {
    }

    /**
     * Returns the best surviving choice, or empty when nothing is usable.
     */
    public static Optional<PlanChoice.RankedChoice> pick(
            PlanChoice choice, Set<String> candidateIds) {
        if (choice == null
                || choice.noSuitableOption()
                || choice.ranking() == null
                || choice.ranking().isEmpty()
                || candidateIds == null
                || candidateIds.isEmpty()) {
            return Optional.empty();
        }

        Set<String> seen = new HashSet<>();

        return choice.ranking().stream()
                .filter(entry -> entry != null)
                .filter(entry -> isUsable(entry, candidateIds, seen))
                .sorted(Comparator
                        .comparingInt(PlanChoice.RankedChoice::rank)
                        .thenComparing(PlanChoice.RankedChoice::id))
                .findFirst();
    }

    private static boolean isUsable(PlanChoice.RankedChoice entry,
                                    Set<String> candidateIds,
                                    Set<String> seen) {
        String id = entry.id();
        if (id == null || id.isBlank()) {
            return false;
        }
        if (!candidateIds.contains(id)) {
            return false;
        }
        if (!seen.add(id)) {
            return false;
        }
        if (entry.rank() <= 0) {
            return false;
        }
        return entry.reason() != null && !entry.reason().isBlank();
    }
}
