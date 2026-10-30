package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.ZonedDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default weather source so the application boots without the weather module.
 *
 * <p>It always reports no rain, which means replanning never generates a
 * weather-driven suggestion outside dev. This bean is replaced by the real NEA
 * implementation, or by {@link FixtureWeatherSource} when the fixture is
 * explicitly enabled.</p>
 */
@Component
@ConditionalOnProperty(name = "app.weather.fixture-enabled", havingValue = "false", matchIfMissing = true)
public class NoRainWeatherSource implements WeatherSource {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean covers(double latitude, double longitude) {
        return false;
    }

    @Override
    public boolean isRainy(double latitude, double longitude,
                           ZonedDateTime from, ZonedDateTime to) {
        return false;
    }
}
