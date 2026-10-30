package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Fetches and parses the Open-Meteo hourly forecast.
 *
 * <p>Open-Meteo returns {@code hourly.time} values without an offset even when
 * {@code timezone=UTC} is requested. They are parsed as {@link LocalDateTime}
 * and pinned to {@link ZoneOffset#UTC}; attaching a region zone would be wrong
 * because the API applies one fixed offset for the whole series and ignores
 * DST.</p>
 */
@Component
public class OpenMeteoClient {

    private static final String HOURLY_VARIABLES =
        "precipitation,rain,showers,precipitation_probability,weather_code";
    private static final int FORECAST_DAYS = 14;

    private final RestClient restClient;

    public OpenMeteoClient(@Qualifier("openMeteoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Fetches the 14-day hourly series for one rounded coordinate. */
    public OpenMeteoForecast fetch(double latitude, double longitude) {
        JsonNode response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v1/forecast")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", HOURLY_VARIABLES)
                .queryParam("timezone", "UTC")
                .queryParam("forecast_days", FORECAST_DAYS)
                .build())
            .retrieve()
            .body(JsonNode.class);

        return parse(response);
    }

    /** Visible for tests: parse the hourly arrays into UTC instants. */
    static OpenMeteoForecast parse(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("Open-Meteo returned an empty response");
        }

        JsonNode error = response.path("error");
        if (error.isBoolean() && error.asBoolean()) {
            throw new IllegalStateException(
                "Open-Meteo error: " + response.path("reason").asText("unknown"));
        }

        JsonNode hourly = response.path("hourly");
        JsonNode times = hourly.path("time");
        if (!times.isArray()) {
            throw new IllegalStateException("Open-Meteo response has no hourly.time array");
        }

        JsonNode precipitation = hourly.path("precipitation");
        JsonNode probability = hourly.path("precipitation_probability");
        JsonNode weatherCode = hourly.path("weather_code");

        List<OpenMeteoForecast.Hour> hours = new ArrayList<>(times.size());
        for (int index = 0; index < times.size(); index++) {
            JsonNode timeNode = times.get(index);
            if (timeNode == null || timeNode.isNull() || timeNode.asText().isBlank()) {
                continue;
            }

            LocalDateTime local = LocalDateTime.parse(timeNode.asText());
            hours.add(new OpenMeteoForecast.Hour(
                local.toInstant(ZoneOffset.UTC),
                nullableDouble(element(precipitation, index)),
                nullableInt(element(weatherCode, index)),
                nullableInt(element(probability, index))));
        }

        return new OpenMeteoForecast(hours);
    }

    private static JsonNode element(JsonNode array, int index) {
        return array.isArray() && index < array.size() ? array.get(index) : null;
    }

    private static Double nullableDouble(JsonNode node) {
        return node == null || node.isNull() ? null : node.asDouble();
    }

    private static Integer nullableInt(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }
}
