package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.time.LocalDate;

public record WeatherForecast(
    LocalDate date,
    String forecast,
    boolean rainExpected
) {}