package com.smu.cs203_g1_1.itinerary_planner.config.region;

import java.util.Locale;

/**
 * Coverage tier for a region, as described in {@code regions.json}.
 */
public enum RegionTier {
    FULL,
    PARTIAL,
    MINIMAL;

    /**
     * Parses a JSON tier value ({@code full}, {@code partial}, {@code minimal})
     * case-insensitively.
     *
     * @throws IllegalArgumentException when the value is missing or unknown
     */
    public static RegionTier fromJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Region tier is required");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown region tier: " + value, ex);
        }
    }
}
