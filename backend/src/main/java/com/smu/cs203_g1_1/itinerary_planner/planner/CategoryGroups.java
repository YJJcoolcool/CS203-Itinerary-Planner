package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.util.Map;

/**
 * Maps OSM {@code (key, value)} tags onto the coarse interest groups used by
 * candidate selection.
 *
 * <p>The table is deliberately small and deterministic: an unrecognised tag
 * yields {@code null}, which callers treat as "no group known".</p>
 */
public final class CategoryGroups {

    private static final Map<String, String> VALUE_KEYS = Map.ofEntries(
            Map.entry("museum", "tourism"),
            Map.entry("gallery", "tourism"),
            Map.entry("attraction", "tourism"),
            Map.entry("theme_park", "tourism"),
            Map.entry("zoo", "tourism"),
            Map.entry("viewpoint", "tourism"),
            Map.entry("park", "leisure"),
            Map.entry("garden", "leisure"),
            Map.entry("nature_reserve", "leisure"),
            Map.entry("bowling_alley", "leisure"),
            Map.entry("mall", "shop"),
            Map.entry("department_store", "shop"),
            Map.entry("restaurant", "amenity"),
            Map.entry("cafe", "amenity"),
            Map.entry("fast_food", "amenity"),
            Map.entry("food_court", "amenity"),
            Map.entry("marketplace", "amenity"),
            Map.entry("library", "amenity"),
            Map.entry("cinema", "amenity"));

    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry(tag("tourism", "museum"), "culture"),
            Map.entry(tag("tourism", "gallery"), "culture"),
            Map.entry(tag("tourism", "attraction"), "attraction"),
            Map.entry(tag("tourism", "theme_park"), "attraction"),
            Map.entry(tag("tourism", "zoo"), "nature"),
            Map.entry(tag("tourism", "viewpoint"), "nature"),
            Map.entry(tag("leisure", "park"), "nature"),
            Map.entry(tag("leisure", "garden"), "nature"),
            Map.entry(tag("leisure", "nature_reserve"), "nature"),
            Map.entry(tag("leisure", "bowling_alley"), "attraction"),
            Map.entry(tag("shop", "mall"), "shopping"),
            Map.entry(tag("shop", "department_store"), "shopping"),
            Map.entry(tag("amenity", "restaurant"), "food"),
            Map.entry(tag("amenity", "cafe"), "food"),
            Map.entry(tag("amenity", "fast_food"), "food"),
            Map.entry(tag("amenity", "food_court"), "food"),
            Map.entry(tag("amenity", "marketplace"), "food"),
            Map.entry(tag("amenity", "library"), "culture"),
            Map.entry(tag("amenity", "cinema"), "culture"));

    private CategoryGroups() {
    }

    /**
     * Returns the coarse group for an OSM tag pair, or {@code null} when the
     * pair is unknown.
     *
     * <p>Inputs are trimmed and lower-cased, and either may be null. When the
     * key is missing, the key is inferred from the value alone: Nominatim
     * occasionally puts the value in either field, so {@code (null, "museum")}
     * is treated the same as {@code ("tourism", "museum")}.</p>
     */
    public static String from(String osmKey, String osmValue) {
        String value = normalise(osmValue);
        if (value == null) {
            return null;
        }

        String key = normalise(osmKey);
        if (key == null) {
            key = inferKey(value);
        }
        return key == null ? null : GROUPS.get(tag(key, value));
    }

    /**
     * Infers the OSM key for a known value ("museum" to "tourism", "park" to
     * "leisure", and so on); returns {@code null} when the value is unknown.
     */
    public static String inferKey(String osmValue) {
        String value = normalise(osmValue);
        return value == null ? null : VALUE_KEYS.get(value);
    }

    private static String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String tag(String key, String value) {
        return key + ":" + value;
    }
}
