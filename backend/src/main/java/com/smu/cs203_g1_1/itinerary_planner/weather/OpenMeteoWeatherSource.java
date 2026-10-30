package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Global Open-Meteo-backed {@link WeatherSource}.
 *
 * <p>Rain-ish WMO codes are treated as wet even when the hourly precipitation
 * total is small. Any positive precipitation above {@code 0.1 mm} also counts,
 * including snow: snow is still precipitation and should trigger indoor
 * alternatives, even though snow codes are not in the rain-code set.</p>
 *
 * <p>Failures are deliberately swallowed: weather is advisory and must never
 * break a replan.</p>
 */
@Component
@ConditionalOnProperty(name = "app.weather.fixture-enabled",
    havingValue = "false", matchIfMissing = true)
public class OpenMeteoWeatherSource implements WeatherSource {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherSource.class);

    private static final Set<Integer> RAIN_CODES = Set.of(
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        80, 81, 82,
        95, 96, 97, 99);

    private static final double RAIN_MM_THRESHOLD = 0.1;
    private static final long CACHE_TTL_MS = Duration.ofMinutes(15).toMillis();
    private static final double COORDINATE_SCALE = 100.0;

    private final OpenMeteoClient client;
    private final Map<Coordinate, CachedForecast> cache = new HashMap<>();

    public OpenMeteoWeatherSource(OpenMeteoClient client) {
        this.client = client;
    }

    @Override
    public String id() {
        return "open-meteo";
    }

    @Override
    public boolean covers(double latitude, double longitude) {
        return true;
    }

    @Override
    public boolean isRainy(double latitude, double longitude,
                           ZonedDateTime from, ZonedDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            return false;
        }

        try {
            OpenMeteoForecast forecast = forecastFor(latitude, longitude);
            Instant fromInstant = from.toInstant();
            Instant toInstant = to.toInstant();

            return forecast.hours().stream().anyMatch(hour ->
                overlaps(hour.time(), fromInstant, toInstant) && isRainy(hour));
        } catch (RuntimeException ex) {
            log.warn("Open-Meteo forecast unavailable at {},{}: {}",
                latitude, longitude, ex.getMessage());
            return false;
        }
    }

    private synchronized OpenMeteoForecast forecastFor(double latitude, double longitude) {
        Coordinate key = new Coordinate(round(latitude), round(longitude));
        long now = System.currentTimeMillis();

        CachedForecast cached = cache.get(key);
        if (cached != null && now - cached.fetchedAt() < CACHE_TTL_MS) {
            return cached.forecast();
        }

        OpenMeteoForecast forecast = client.fetch(key.latitude(), key.longitude());
        cache.put(key, new CachedForecast(forecast, now));
        return forecast;
    }

    /**
     * An Open-Meteo hourly entry labelled {@code T} covers {@code [T-1h, T)}.
     * It overlaps the window {@code [from, to]} when it starts at or before
     * {@code to} and ends strictly after {@code from}.
     */
    private static boolean overlaps(Instant hourEnd, Instant from, Instant to) {
        return !hourEnd.minus(Duration.ofHours(1)).isAfter(to)
            && hourEnd.isAfter(from);
    }

    private static boolean isRainy(OpenMeteoForecast.Hour hour) {
        if (hour.precipitationMm() != null
                && hour.precipitationMm() > RAIN_MM_THRESHOLD) {
            return true;
        }
        return hour.weatherCode() != null
            && RAIN_CODES.contains(hour.weatherCode());
    }

    private static double round(double value) {
        return Math.round(value * COORDINATE_SCALE) / COORDINATE_SCALE;
    }

    private record Coordinate(double latitude, double longitude) {
    }

    private record CachedForecast(OpenMeteoForecast forecast, long fetchedAt) {
    }
}
