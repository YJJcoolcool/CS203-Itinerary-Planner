package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps trip goals and free-text interest chips onto the coarse interest groups
 * used by {@link ScoreCalculator}.
 *
 * <p>This is a curated, deterministic lookup table, not a model: the same input
 * always produces the same groups. Inputs are trimmed and lower-cased, null and
 * blank entries are ignored, and unknown values are skipped silently.</p>
 */
public final class CategoryGoalMapper {

    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("museums", "culture"),
            Map.entry("art", "culture"),
            Map.entry("history", "culture"),
            Map.entry("culture", "culture"),
            Map.entry("nature", "nature"),
            Map.entry("photography", "nature"),
            Map.entry("food", "food"),
            Map.entry("shopping", "shopping"),
            Map.entry("nightlife", "nightlife"),
            Map.entry("attractions", "attraction"),
            Map.entry("family", "attraction"));

    private CategoryGoalMapper() {
    }

    /**
     * Returns the known groups for the supplied values, ignoring nulls, blanks
     * and unrecognised values. Never throws.
     */
    public static Set<String> toGroups(Collection<String> values) {
        Set<String> groups = new LinkedHashSet<>();
        if (values == null) {
            return groups;
        }

        for (String value : values) {
            if (value == null) {
                continue;
            }
            String key = value.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            String group = GROUPS.get(key);
            if (group != null) {
                groups.add(group);
            }
        }

        return groups;
    }
}
