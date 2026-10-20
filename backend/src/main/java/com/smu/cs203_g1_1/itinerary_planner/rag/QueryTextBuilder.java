package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the natural-language query embedded for semantic retrieval.
 *
 * <p>The text is assembled in a fixed order so identical inputs produce an
 * identical string — that keeps the query cache effective and makes ranking
 * reproducible. Tags are sorted for the same reason.</p>
 */
public final class QueryTextBuilder {

    private QueryTextBuilder() {
    }

    public static String build(String tripGoal,
                               String groupSummary,
                               Set<String> travellerTags,
                               String weatherNote) {
        StringBuilder query = new StringBuilder();

        append(query, "Trip goal", tripGoal);
        append(query, "Group preferences", groupSummary);

        if (travellerTags != null && !travellerTags.isEmpty()) {
            append(query, "Travellers like",
                String.join(", ", new TreeSet<>(travellerTags)));
        }

        append(query, "Conditions", weatherNote);

        return query.isEmpty() ? "places to visit in Singapore" : query.toString().trim();
    }

    private static void append(StringBuilder query, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (!query.isEmpty()) {
            query.append(". ");
        }

        query.append(label).append(": ").append(value.trim());
    }
}
