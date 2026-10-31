package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionTier;
import com.smu.cs203_g1_1.itinerary_planner.config.region.TileConfig;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.weather.OpenMeteoForecast;
import com.smu.cs203_g1_1.itinerary_planner.weather.OpenMeteoWeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherOutlookServiceTest {

    private static final String TRIP_ID = "Trip0001";
    private static final double LAT = 35.6762;
    private static final double LON = 139.6503;
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private RegionRegistry regionRegistry;

    @Mock
    private WeatherSourceResolver weatherSourceResolver;

    @Mock
    private OpenMeteoWeatherSource openMeteoSource;

    @Mock
    private WeatherSource provider;

    private WeatherOutlookService service;

    @BeforeEach
    void setUp() {
        service = new WeatherOutlookService(
            activityRepository, regionRegistry, weatherSourceResolver,
            openMeteoSource, CLOCK);
    }

    @Test
    void bucketsAcrossTokyoDaysAndAggregatesInLocalTime() {
        givenTripAndPoint(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
        givenProvider("open-meteo");
        givenForecast(
            hour("2026-11-02T00:00:00Z", 0.2, 61, 10),
            hour("2026-11-02T01:00:00Z", 0.3, 80, 60),
            hour("2026-11-02T16:00:00Z", 0.4, 3, 30));

        List<DailyOutlook> outlook = service.outlook(trip(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)));

        assertEquals(2, outlook.size());

        DailyOutlook first = outlook.get(0);
        assertEquals(LocalDate.of(2026, 11, 2), first.date());
        assertEquals(0.5, first.precipitationMm(), 1e-9);
        assertEquals(60, first.probabilityPercent());
        assertEquals(80, first.weatherCode());
        assertTrue(first.rainy());

        DailyOutlook second = outlook.get(1);
        assertEquals(LocalDate.of(2026, 11, 3), second.date());
        assertEquals(0.4, second.precipitationMm(), 1e-9);
        assertEquals(30, second.probabilityPercent());
        assertEquals(3, second.weatherCode());
        assertTrue(second.rainy());
    }

    @Test
    void codeOnlyHourMakesTheDayRainy() {
        givenTripAndPoint(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        givenProvider("open-meteo");
        givenForecast(hour("2026-11-02T03:00:00Z", 0.0, 61, 5));

        List<DailyOutlook> outlook = service.outlook(trip(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2)));

        assertEquals(1, outlook.size());
        assertTrue(outlook.get(0).rainy());
    }

    @Test
    void dailyTotalAboveThresholdIsRainyEvenWhenEachHourIsSmall() {
        givenTripAndPoint(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        givenProvider("open-meteo");
        givenForecast(
            hour("2026-11-02T03:00:00Z", 0.06, 3, 20),
            hour("2026-11-02T04:00:00Z", 0.06, 3, 20));

        List<DailyOutlook> outlook = service.outlook(trip(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2)));

        assertEquals(1, outlook.size());
        assertTrue(outlook.get(0).rainy());
        assertEquals(0.12, outlook.get(0).precipitationMm(), 1e-9);
    }

    @Test
    void neaProviderProducesNoPointOutlook() {
        givenTripAndPoint(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        givenProvider("nea");

        List<DailyOutlook> outlook = service.outlook(trip(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2)));

        assertTrue(outlook.isEmpty());
        verifyNoInteractions(openMeteoSource);
    }

    @Test
    void tripWithoutALocatedActivityProducesNoOutlook() {
        Trip trip = trip(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        when(regionRegistry.byCode("jp")).thenReturn(Optional.of(tokyoRegion()));

        Location unlocated = new Location("No coords", "test", null, null, true);
        unlocated.setId("Loc00001");
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc(TRIP_ID))
            .thenReturn(List.of(activity(unlocated)));

        assertTrue(service.outlook(trip).isEmpty());
        verifyNoInteractions(weatherSourceResolver, openMeteoSource);
    }

    @Test
    void dateRangeIsClampedToTripAndFourteenDayForecast() {
        Trip trip = trip(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 30));
        givenRegionAndPoint();
        givenProvider("open-meteo");
        givenForecast(
            hour("2026-11-01T03:00:00Z", 0.5, 61, 10),
            hour("2026-11-02T03:00:00Z", 0.5, 61, 10),
            hour("2026-11-16T03:00:00Z", 0.5, 61, 10),
            hour("2026-11-17T03:00:00Z", 0.5, 61, 10));

        List<DailyOutlook> outlook = service.outlook(trip);

        assertEquals(List.of(
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 16)),
            outlook.stream().map(DailyOutlook::date).toList());
    }

    @Test
    void emptyForecastProducesNoOutlook() {
        givenTripAndPoint(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        givenProvider("open-meteo");
        when(openMeteoSource.forecastForExact(LAT, LON))
            .thenReturn(Optional.empty());

        assertTrue(service.outlook(trip(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2))).isEmpty());
    }

    private void givenTripAndPoint(LocalDate start, LocalDate end) {
        givenRegionAndPoint();
    }

    private void givenRegionAndPoint() {
        when(regionRegistry.byCode("jp")).thenReturn(Optional.of(tokyoRegion()));

        Location location = new Location("Senso-ji", "test", LAT, LON, true);
        location.setId("Loc00001");
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc(TRIP_ID))
            .thenReturn(List.of(activity(location)));
    }

    private void givenProvider(String id) {
        when(weatherSourceResolver.forPoint(LAT, LON))
            .thenReturn(Optional.of(provider));
        when(provider.id()).thenReturn(id);
    }

    private void givenForecast(OpenMeteoForecast.Hour... hours) {
        when(openMeteoSource.forecastForExact(LAT, LON))
            .thenReturn(Optional.of(new OpenMeteoForecast(List.of(hours))));
    }

    private static Activity activity(Location location) {
        Activity activity = new Activity(
            "Activity", List.of(location),
            ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, TOKYO),
            ZonedDateTime.of(2026, 11, 2, 11, 0, 0, 0, TOKYO),
            "PLANNED");
        activity.setId("Act00001");
        return activity;
    }

    private static Trip trip(LocalDate start, LocalDate end) {
        User owner = new User("clerk-owner", "Owner");
        owner.setId("User0001");
        Trip trip = new Trip("Japan", start, end, owner);
        trip.setId(TRIP_ID);
        trip.setCountryCode("jp");
        return trip;
    }

    private static RegionProfile tokyoRegion() {
        return new RegionProfile(
            "jp",
            "Japan",
            RegionTier.FULL,
            "en",
            "JPY",
            TOKYO,
            List.of(),
            Map.of("weather", "open-meteo"),
            new TileConfig(null, null, "wgs84"));
    }

    private static OpenMeteoForecast.Hour hour(String time, double precipitationMm,
                                               int weatherCode, int probabilityPercent) {
        return new OpenMeteoForecast.Hour(
            Instant.parse(time), precipitationMm, weatherCode, probabilityPercent);
    }
}
