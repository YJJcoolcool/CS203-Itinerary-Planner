package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.time.OffsetDateTime;

public record TwoHourWeatherForecast(
    String area,
    String forecast,
    OffsetDateTime validFrom,
    OffsetDateTime validUntil,
    OffsetDateTime updatedAt,
    boolean rainExpected
) {}
