package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic weather source for local demos and tests only.
 *
 * <p>It answers the configured {@code app.weather.fixture-rainy} value for
 * every request, regardless of place or time, so the suggestion flow can be
 * exercised before the NEA feed exists. It is never used in production.</p>
 */
@Component
@ConditionalOnProperty(name = "app.weather.fixture-enabled", havingValue = "true")
public class FixtureWeatherSource implements WeatherSource {

    private final boolean rainy;

    /**
     * Spring-injected constructor. Tests can call it directly with a boolean
     * because the {@code @Value} is resolved at construction time.
     */
    @Autowired
    public FixtureWeatherSource(@Value("${app.weather.fixture-rainy:true}") boolean rainy) {
        this.rainy = rainy;
    }

    @Override
    public String id() {
        return "fixture";
    }

    @Override
    public boolean isRainy(double latitude, double longitude,
                           ZonedDateTime from, ZonedDateTime to) {
        return rainy;
    }
}
