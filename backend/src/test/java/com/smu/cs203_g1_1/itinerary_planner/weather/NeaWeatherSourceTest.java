package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.smu.cs203_g1_1.itinerary_planner.services.weather.NEAWeatherClient;
import com.smu.cs203_g1_1.itinerary_planner.services.weather.TwoHourWeatherForecast;
import com.smu.cs203_g1_1.itinerary_planner.services.weather.WeatherForecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NeaWeatherSourceTest {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    private static final NeaWeatherSource.Area BEDOK =
        new NeaWeatherSource.Area("Bedok", 1.321, 103.924);
    private static final NeaWeatherSource.Area TAMPINES =
        new NeaWeatherSource.Area("Tampines", 1.345, 103.944);

    private static final OffsetDateTime FEED_FROM =
        OffsetDateTime.parse("2026-10-20T22:30:00+08:00");
    private static final OffsetDateTime FEED_TO =
        OffsetDateTime.parse("2026-10-21T00:30:00+08:00");

    private static final ZonedDateTime ACTIVITY_START =
        ZonedDateTime.of(2026, 10, 20, 23, 0, 0, 0, SINGAPORE);

    @Mock
    private NEAWeatherClient client;

    private NeaWeatherSource source;

    @BeforeEach
    void setUp() {
        source = new NeaWeatherSource(
            client,
            List.of(BEDOK, TAMPINES),
            Clock.system(SINGAPORE)
        );
    }

    @Test
    void rainInTheActivityAreaReturnsTrue() {
        when(client.getTwoHourForecast()).thenReturn(List.of(
            twoHour("Bedok", true),
            twoHour("Tampines", false)
        ));

        assertTrue(source.isRainy(
            1.3200, 103.9230,
            ACTIVITY_START, ACTIVITY_START.plusMinutes(30)
        ));
    }

    @Test
    void dryAreaReturnsFalse() {
        when(client.getTwoHourForecast()).thenReturn(List.of(
            twoHour("Bedok", false)
        ));

        assertFalse(source.isRainy(
            1.3200, 103.9230,
            ACTIVITY_START, ACTIVITY_START.plusMinutes(30)
        ));
    }

    @Test
    void pointOutsideSingaporeIsNeverEvaluated() {
        assertFalse(source.isRainy(
            35.6762, 139.6503,
            ACTIVITY_START, ACTIVITY_START.plusHours(1)
        ));

        verifyNoInteractions(client);
    }

    @Test
    void missingAreaFallsBackToTheFourDayOutlook() {
        when(client.getTwoHourForecast()).thenReturn(List.of(
            twoHour("Tampines", false)
        ));
        when(client.getFourDayForecast()).thenReturn(List.of(
            new WeatherForecast(LocalDate.of(2026, 10, 20), "Thundery Showers", true)
        ));

        assertTrue(source.isRainy(
            1.3200, 103.9230,
            ACTIVITY_START, ACTIVITY_START.plusMinutes(30)
        ));
    }

    @Test
    void longWindowIgnoresTheTwoHourFeedAndUsesTheFourDayOutlook() {
        when(client.getTwoHourForecast()).thenReturn(List.of(
            twoHour("Bedok", true)
        ));
        when(client.getFourDayForecast()).thenReturn(List.of(
            new WeatherForecast(LocalDate.of(2026, 10, 20), "Cloudy", false),
            new WeatherForecast(LocalDate.of(2026, 10, 21), "Cloudy", false)
        ));

        assertFalse(source.isRainy(
            1.3200, 103.9230,
            ACTIVITY_START, ACTIVITY_START.plusMinutes(150)
        ));
    }

    @Test
    void upstreamFailureIsTreatedAsNoRain() {
        when(client.getTwoHourForecast())
            .thenThrow(new IllegalStateException("NEA unavailable"));

        assertFalse(source.isRainy(
            1.3200, 103.9230,
            ACTIVITY_START, ACTIVITY_START.plusMinutes(30)
        ));
    }

    @Test
    void nullOrInvertedWindowReturnsFalse() {
        assertFalse(source.isRainy(1.3200, 103.9230, null, ACTIVITY_START));
        assertFalse(source.isRainy(
            1.3200, 103.9230,
            ACTIVITY_START.plusHours(1), ACTIVITY_START
        ));
    }

    @Test
    void nearestAreaUsesTheClosestPublishedPoint() {
        assertEquals("Bedok", source.nearestArea(1.3200, 103.9230).name());
        assertEquals("Tampines", source.nearestArea(1.3450, 103.9440).name());
    }

    @Test
    void classpathAreaTableLoadsAndMapsAKnownPlace() {
        NeaWeatherSource fromResource = new NeaWeatherSource(client);

        assertEquals("Tampines", fromResource.nearestArea(1.3450, 103.9440).name());
        assertEquals("Bedok", fromResource.nearestArea(1.3210, 103.9240).name());
    }

    private static TwoHourWeatherForecast twoHour(String area, boolean rainy) {
        return new TwoHourWeatherForecast(
            area,
            rainy ? "Thundery Showers" : "Cloudy",
            FEED_FROM,
            FEED_TO,
            FEED_FROM,
            rainy
        );
    }
}
