package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.time.LocalDate;

/** One day of point-based weather for a non-NEA destination. */
public record DailyOutlook(
    LocalDate date,
    boolean rainy,
    double precipitationMm,
    Integer probabilityPercent,
    Integer weatherCode
) {
}
