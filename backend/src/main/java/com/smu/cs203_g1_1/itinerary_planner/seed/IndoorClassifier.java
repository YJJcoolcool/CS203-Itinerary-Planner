package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.util.Map;
import java.util.Set;

/**
 * Heuristic indoor/outdoor classifier over OSM tags.
 *
 * <p>This is a rule table, not ground truth: OSM tagging is incomplete and
 * sometimes wrong. Callers must treat {@code UNKNOWN} as "we do not know" and
 * never as indoor; rain swaps exclude unknown places. The rules are ordered so
 * explicit tags beat category defaults, and category defaults beat unknown.</p>
 */
public class IndoorClassifier {

    private static final Set<String> EXPLICIT_INDOOR = Set.of("yes", "room", "corridor", "true", "1");
    private static final Set<String> SHOP_INDOOR = Set.of("mall");
    private static final Set<String> TOURISM_INDOOR = Set.of("museum", "gallery");
    private static final Set<String> AMENITY_INDOOR = Set.of(
            "restaurant", "cafe", "fast_food", "bar", "cinema", "library");
    private static final Set<String> LEISURE_OUTDOOR = Set.of("park", "garden", "nature_reserve");

    /**
     * Classifies a place from its OSM tags. Never throws: a null tag map is
     * treated as empty and unknown input falls through to {@code UNKNOWN}.
     */
    public IndoorClassification classify(String category, Map<String, String> tags) {
        Map<String, String> safeTags = tags == null ? Map.of() : tags;

        // 1. Explicit indoor / covered evidence wins over every category signal.
        if (isOneOf(EXPLICIT_INDOOR, safeTags, "indoor")
                || "yes".equals(value(safeTags, "covered"))) {
            return new IndoorClassification("INDOOR", "OSM_TAG", 0.95);
        }

        // 2. Strong indoor categories.
        if (isOneOf(SHOP_INDOOR, safeTags, "shop")
                || isOneOf(TOURISM_INDOOR, safeTags, "tourism")) {
            return new IndoorClassification("INDOOR", "OSM_TAG", 0.9);
        }

        // 3. Amenities that are normally indoors.
        if (isOneOf(AMENITY_INDOOR, safeTags, "amenity")) {
            return new IndoorClassification("INDOOR", "OSM_TAG", 0.85);
        }

        // 4. Outdoor categories.
        if (isOneOf(LEISURE_OUTDOOR, safeTags, "leisure")
                || "viewpoint".equals(value(safeTags, "tourism"))
                || value(safeTags, "natural") != null) {
            return new IndoorClassification("OUTDOOR", "OSM_TAG", 0.85);
        }

        // 5. Ambiguous categories: could be either, so stay unknown.
        if ("attraction".equals(value(safeTags, "tourism"))
                || "place_of_worship".equals(value(safeTags, "amenity"))) {
            return new IndoorClassification("UNKNOWN", null, 0.3);
        }

        // 6. A building tag alone is weak indoor evidence.
        if (value(safeTags, "building") != null) {
            return new IndoorClassification("INDOOR", "OSM_TAG", 0.6);
        }

        // 7. Nothing usable.
        return new IndoorClassification("UNKNOWN", null, 0.0);
    }

    private static boolean isOneOf(Set<String> allowed, Map<String, String> tags, String key) {
        String raw = value(tags, key);
        return raw != null && allowed.contains(raw);
    }

    private static String value(Map<String, String> tags, String key) {
        String raw = tags.get(key);
        return raw == null ? null : raw.trim().toLowerCase();
    }

    /**
     * One classifier decision. {@code indoorClass} is {@code INDOOR},
     * {@code OUTDOOR}, or {@code UNKNOWN}; {@code source} is {@code OSM_TAG}
     * (or {@code null}); {@code confidence} is in {@code [0, 1]}.
     */
    public record IndoorClassification(String indoorClass, String source, double confidence) {
    }
}
