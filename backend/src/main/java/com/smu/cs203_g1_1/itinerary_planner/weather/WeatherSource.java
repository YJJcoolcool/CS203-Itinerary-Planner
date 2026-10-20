package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.ZonedDateTime;

/**
 * Seam between the suggestion flow and the weather forecast.
 *
 * <p>This is the extension point for the teammate's NEA 2-hour feed. An
 * implementation answers only the one question the replanner asks: is rain
 * expected at this place during this window?</p>
 *
 * <p>Implementations must return {@code false} when no forecast is available
 * for the requested area or time. They must never throw for missing data, so a
 * missing feed can only ever suppress a suggestion, not break a request.</p>
 */
public interface WeatherSource {

    boolean isRainy(double latitude, double longitude,
                    ZonedDateTime from, ZonedDateTime to);
}
