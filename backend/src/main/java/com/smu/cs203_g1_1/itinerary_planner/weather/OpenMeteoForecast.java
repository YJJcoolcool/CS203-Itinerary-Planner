package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.Instant;
import java.util.List;

/** Parsed hourly series returned by Open-Meteo. */
public record OpenMeteoForecast(List<Hour> hours) {

    public OpenMeteoForecast {
        hours = hours == null ? List.of() : List.copyOf(hours);
    }

    /**
     * One hourly entry. {@code time} is the end of the interval
     * {@code [time - 1h, time)} and is always a UTC instant.
     */
    public record Hour(Instant time,
                       Double precipitationMm,
                       Integer weatherCode,
                       Integer probabilityPercent) {

        /** Convenience constructor for callers that do not need probability. */
        public Hour(Instant time, Double precipitationMm, Integer weatherCode) {
            this(time, precipitationMm, weatherCode, null);
        }
    }
}
