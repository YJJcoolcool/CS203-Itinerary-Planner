package com.smu.cs203_g1_1.itinerary_planner.rag;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;

/**
 * Renders a place as the short natural-language document that gets embedded.
 *
 * <p>OSM tags are sparse, so the text spells out what a reader would infer:
 * the category, its interest group, whether it is indoors, and any address or
 * opening hours. Coordinates and ids are deliberately excluded — they carry no
 * semantic meaning and would only add noise to the vector.</p>
 */
public final class PlaceDocumentBuilder {

    private PlaceDocumentBuilder() {
    }

    public static String build(Location location) {
        if (location == null) {
            return "";
        }

        StringBuilder document = new StringBuilder();

        append(document, location.getName());

        String category = describeCategory(location);

        if (category != null) {
            append(document, category);
        }

        append(document, describeIndoor(location));
        append(document, prefixed("Address", location.getAddress()));
        append(document, prefixed("Opening hours", location.getOpeningHours()));

        return document.toString().trim();
    }

    private static String describeCategory(Location location) {
        String category = blankToNull(location.getCategory());
        String group = blankToNull(location.getCategoryGroup());

        if (category == null && group == null) {
            return null;
        }

        if (category == null) {
            return group + " place";
        }

        if (group == null) {
            return category;
        }

        return category + ", part of the " + group + " group";
    }

    private static String describeIndoor(Location location) {
        String indoorClass = blankToNull(location.getIndoorClass());

        if (indoorClass == null) {
            return null;
        }

        return switch (indoorClass.toUpperCase()) {
            case "INDOOR" -> "Indoors, suitable when it rains.";
            case "OUTDOOR" -> "Outdoors, exposed to the weather.";
            default -> null;
        };
    }

    private static String prefixed(String label, String value) {
        String trimmed = blankToNull(value);

        return trimmed == null ? null : label + ": " + trimmed + ".";
    }

    private static void append(StringBuilder document, String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return;
        }

        if (!document.isEmpty()) {
            document.append(' ');
        }

        document.append(sentence.trim());

        if (!sentence.endsWith(".") && !sentence.endsWith("。")) {
            document.append('.');
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
