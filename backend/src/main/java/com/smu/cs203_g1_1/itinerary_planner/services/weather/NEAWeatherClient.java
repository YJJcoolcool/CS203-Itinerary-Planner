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

    private static final String FOUR_DAY_API_URL =
        "https://api-open.data.gov.sg/v2/real-time/api/four-day-outlook";
    private static final String TWO_HOUR_API_URL =
        "https://api-open.data.gov.sg/v2/real-time/api/two-hr-forecast";

    private final RestClient restClient;

    private static final long CACHE_DURATION_MS =
        15 * 60 * 1000L;

    private List<WeatherForecast> cachedFourDayForecast;
    private long fourDayCacheTimestamp;
    private List<TwoHourWeatherForecast> cachedTwoHourForecast;
    private long twoHourCacheTimestamp;

    public NEAWeatherClient() {
        this.restClient = RestClient.create();
    }

    public synchronized List<WeatherForecast> getFourDayForecast() {
        long now = System.currentTimeMillis();

        if (cachedFourDayForecast != null
                && now - fourDayCacheTimestamp < CACHE_DURATION_MS) {
            return cachedFourDayForecast;
        }

        List<WeatherForecast> forecasts = fetchForecastFromNEA();

        cachedFourDayForecast = List.copyOf(forecasts);
        fourDayCacheTimestamp = now;

        return cachedFourDayForecast;
    }

    public synchronized List<TwoHourWeatherForecast> getTwoHourForecast() {
        long now = System.currentTimeMillis();

        if (cachedTwoHourForecast != null
                && now - twoHourCacheTimestamp < CACHE_DURATION_MS) {
            return cachedTwoHourForecast;
        }

        List<TwoHourWeatherForecast> forecasts =
            fetchTwoHourForecastFromNEA();

        cachedTwoHourForecast = List.copyOf(forecasts);
        twoHourCacheTimestamp = now;

        return cachedTwoHourForecast;
    }

    private List<TwoHourWeatherForecast> fetchTwoHourForecastFromNEA() {
        JsonNode response = restClient.get()
            .uri(TWO_HOUR_API_URL)
            .retrieve()
            .body(JsonNode.class);

        if (response == null || response.path("code").asInt(-1) != 0) {
            throw new IllegalStateException(
                "Unable to retrieve the NEA two-hour weather forecast"
            );
        }

        JsonNode data = response.path("data");
        JsonNode items = data.path("items");

        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalStateException(
                "NEA returned no two-hour weather forecast records"
            );
        }

        List<TwoHourWeatherForecast> forecasts = new ArrayList<>();

        for (JsonNode item : items) {
            JsonNode validPeriod = item.path("valid_period");

            String validFromText = validPeriod.path("start").asText("");
            String validUntilText = validPeriod.path("end").asText("");
            String updatedAtText = item.path("update_timestamp").asText("");

            if (validFromText.isBlank() || validUntilText.isBlank()) {
                continue;
            }

            OffsetDateTime validFrom = OffsetDateTime.parse(validFromText);
            OffsetDateTime validUntil = OffsetDateTime.parse(validUntilText);

            OffsetDateTime updatedAt = updatedAtText.isBlank()
                ? null
                : OffsetDateTime.parse(updatedAtText);

            JsonNode areaForecasts = item.path("forecasts");

            if (!areaForecasts.isArray()) {
                continue;
            }

            for (JsonNode areaForecast : areaForecasts) {
                String area = areaForecast.path("area").asText("");
                String description = areaForecast.path("forecast").asText("");

                if (area.isBlank() || description.isBlank()) {
                    continue;
                }

                forecasts.add(new TwoHourWeatherForecast(
                    area,
                    description,
                    validFrom,
                    validUntil,
                    updatedAt,
                    indicatesRain(description)
                ));
            }
        }

        if (forecasts.isEmpty()) {
            throw new IllegalStateException(
                "NEA returned no usable two-hour weather forecasts"
            );
        }

        return forecasts;
    }

    private List<WeatherForecast> fetchForecastFromNEA() {
        JsonNode response = restClient.get()
            .uri(FOUR_DAY_API_URL)
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