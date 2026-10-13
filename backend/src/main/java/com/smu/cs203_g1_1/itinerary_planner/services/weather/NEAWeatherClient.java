package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class NEAWeatherClient {

    private static final String API_URL =
        "https://api-open.data.gov.sg/v2/real-time/api/four-day-outlook";

    private final RestClient restClient;

    private static final long CACHE_DURATION_MS =
        15 * 60 * 1000L;

    private List<WeatherForecast> cachedForecast;
    private long cacheTimestamp;

    public NEAWeatherClient() {
        this.restClient = RestClient.create();
    }

    public synchronized List<WeatherForecast> getForecast() {
        long now = System.currentTimeMillis();

        if (cachedForecast != null
                && now - cacheTimestamp < CACHE_DURATION_MS) {
            return cachedForecast;
        }

        List<WeatherForecast> forecasts = fetchForecastFromNEA();

        cachedForecast = List.copyOf(forecasts);
        cacheTimestamp = now;

        return cachedForecast;
    }

    private List<WeatherForecast> fetchForecastFromNEA() {
        JsonNode response = restClient.get()
            .uri(API_URL)
            .retrieve()
            .body(JsonNode.class);

        if (response == null || response.path("code").asInt(-1) != 0) {
            throw new IllegalStateException(
                "Unable to retrieve the NEA weather forecast"
            );
        }

        JsonNode records = response.path("data").path("records");

        if (!records.isArray() || records.isEmpty()) {
            throw new IllegalStateException(
                "NEA returned no weather forecast records"
            );
        }

        List<WeatherForecast> forecasts = new ArrayList<>();

        for (JsonNode record : records) {
            JsonNode dailyForecasts = record.path("forecasts");

            if (!dailyForecasts.isArray()) {
                continue;
            }

            for (JsonNode daily : dailyForecasts) {
                String timestamp = daily.path("timestamp").asText("");
                String description = daily.path("forecast")
                    .path("text")
                    .asText("");

                if (timestamp.isBlank() || description.isBlank()) {
                    continue;
                }

                LocalDate date = OffsetDateTime.parse(timestamp)
                    .toLocalDate();

                forecasts.add(new WeatherForecast(
                    date,
                    description,
                    indicatesRain(description)
                ));
            }
        }

        return forecasts;
    }

    private boolean indicatesRain(String description) {
        String text = description.toLowerCase();

        return text.contains("rain")
            || text.contains("shower")
            || text.contains("thunderstorm")
            || text.contains("thundery");
    }
}