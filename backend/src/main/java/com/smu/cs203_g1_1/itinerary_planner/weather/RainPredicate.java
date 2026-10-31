package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.util.Set;

/** Shared rain rule for Open-Meteo hourly values. */
final class RainPredicate {

    static final Set<Integer> RAIN_CODES = Set.of(
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        80, 81, 82,
        95, 96, 97, 99);

    static final double RAIN_MM_THRESHOLD = 0.1;

    private RainPredicate() {
    }

    static boolean isRainy(Double precipitationMm, Integer weatherCode) {
        if (precipitationMm != null && precipitationMm > RAIN_MM_THRESHOLD) {
            return true;
        }
        return isRainCode(weatherCode);
    }

    static boolean isRainCode(Integer weatherCode) {
        return weatherCode != null && RAIN_CODES.contains(weatherCode);
    }
}
