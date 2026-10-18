package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.util.Map;
import java.util.Set;

/**
 * Hand-curated similarity between the coarse interest groups. This is a fixed
 * table, not a model: the same two groups always produce the same value.
 *
 * <p>The table is symmetric, the diagonal is {@code 1.0}, and any pair of known
 * groups that is not listed explicitly falls back to {@code 0.2}. Null, blank,
 * or unknown groups score {@code 0.0}.</p>
 */
public final class CategorySimilarity {

    private static final Set<String> GROUPS = Set.of(
            "culture", "nature", "attraction", "shopping", "food");

    private static final double DEFAULT_UNRELATED = 0.2;

    private static final Map<String, Double> SIMILARITIES = Map.ofEntries(
            Map.entry(pair("culture", "attraction"), 0.5),
            Map.entry(pair("culture", "shopping"), 0.3),
            Map.entry(pair("culture", "food"), 0.2),
            Map.entry(pair("nature", "attraction"), 0.6),
            Map.entry(pair("nature", "shopping"), 0.1),
            Map.entry(pair("nature", "food"), 0.1),
            Map.entry(pair("attraction", "shopping"), 0.2),
            Map.entry(pair("attraction", "food"), 0.3),
            Map.entry(pair("shopping", "food"), 0.4));

    private CategorySimilarity() {
    }

    public static double similarity(String a, String b) {
        String left = normalise(a);
        String right = normalise(b);

        if (!isKnownGroup(left) || !isKnownGroup(right)) {
            return 0.0;
        }
        if (left.equals(right)) {
            return 1.0;
        }
        return SIMILARITIES.getOrDefault(pair(left, right), DEFAULT_UNRELATED);
    }

    public static boolean isKnownGroup(String group) {
        return group != null && GROUPS.contains(normalise(group));
    }

    private static String normalise(String group) {
        return group == null ? null : group.trim().toLowerCase();
    }

    private static String pair(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
