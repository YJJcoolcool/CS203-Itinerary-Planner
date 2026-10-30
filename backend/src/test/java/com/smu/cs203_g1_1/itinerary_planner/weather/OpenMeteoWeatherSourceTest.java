package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OpenMeteoWeatherSourceTest {

    private static final double LAT = 35.6762;
    private static final double LON = 139.6503;
    /** Window start. An entry labelled at this instant covers [09:00, 10:00). */
    private static final Instant RAIN_HOUR = Instant.parse("2026-11-02T10:00:00Z");
    /** Covers [10:00, 11:00), i.e. strictly inside the standard window. */
    private static final Instant IN_HOUR = RAIN_HOUR.plus(Duration.ofHours(1));

    private OpenMeteoClient client;
    private OpenMeteoWeatherSource source;

    @BeforeEach
    void setUp() {
        client = mock(OpenMeteoClient.class);
        source = new OpenMeteoWeatherSource(client);
    }

    @Test
    void rainInsideTheWindowReturnsTrue() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(IN_HOUR, 0.5, 3)));

        assertTrue(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void hourBeforeTheWindowIsIgnored() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(RAIN_HOUR.minus(Duration.ofHours(1)), 0.5, 61)));

        // The entry covers [08:00, 09:00); the window starts at 10:00.
        assertFalse(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void hourEndingAtWindowStartIsIgnored() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(RAIN_HOUR, 0.5, 61)));

        assertFalse(source.isRainy(LAT, LON,
            at(RAIN_HOUR).plusMinutes(0), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void hourOverlappingAWindowThatStartsMidHourCounts() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(RAIN_HOUR.plus(Duration.ofHours(1)), 0.5, 61)));

        // Entry covers [10:00, 11:00); window starts 10:30.
        assertTrue(source.isRainy(LAT, LON,
            at(RAIN_HOUR).plusMinutes(30), at(RAIN_HOUR).plusHours(3)));
    }

    @Test
    void dryWindowReturnsFalse() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(IN_HOUR, 0.05, 3)));

        assertFalse(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void windowWithNoDataReturnsFalse() {
        when(client.fetch(anyDouble(), anyDouble()))
            .thenReturn(new OpenMeteoForecast(List.of()));

        assertFalse(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void upstreamFailureReturnsFalseAndNeverThrows() {
        when(client.fetch(anyDouble(), anyDouble()))
            .thenThrow(new IllegalStateException("Open-Meteo 429"));

        assertFalse(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void snowPrecipitationAlsoCountsAsRainy() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(IN_HOUR, 0.5, 71)));

        assertTrue(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void rainWeatherCodeWithZeroMillimetresCountsAsRainy() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(IN_HOUR, 0.0, 61)));

        assertTrue(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void nullValuesAreSkipped() {
        when(client.fetch(anyDouble(), anyDouble())).thenReturn(series(
            new OpenMeteoForecast.Hour(IN_HOUR, null, null)));

        assertFalse(source.isRainy(LAT, LON,
            at(RAIN_HOUR), at(RAIN_HOUR).plusHours(2)));
    }

    @Test
    void hourlyTimesAreParsedAsUtcAcrossADstBoundary() throws Exception {
        String json = """
            {"hourly":{
              "time":["2026-03-29T02:00"],
              "precipitation":[1.0],
              "weather_code":[3]
            }}
            """;
        OpenMeteoForecast parsed = OpenMeteoClient.parse(
            new ObjectMapper().readTree(json));

        // Offset-less label is pinned to UTC, not to the region zone.
        assertEquals(Instant.parse("2026-03-29T02:00:00Z"),
            parsed.hours().get(0).time());

        when(client.fetch(anyDouble(), anyDouble())).thenReturn(parsed);

        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime from = ZonedDateTime.of(2026, 3, 29, 3, 0, 0, 0, berlin);
        ZonedDateTime to = from.plusHours(2);

        assertTrue(source.isRainy(LAT, LON, from, to));
    }

    private static OpenMeteoForecast series(OpenMeteoForecast.Hour... hours) {
        return new OpenMeteoForecast(List.of(hours));
    }

    private static ZonedDateTime at(Instant instant) {
        return instant.atZone(ZoneId.of("UTC"));
    }
}
