package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionTier;
import com.smu.cs203_g1_1.itinerary_planner.config.region.TileConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherSourceResolverTest {

    @Test
    void singaporeUsesTheConfiguredNeaProvider() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("sg")).thenReturn(Optional.of(region("sg", "nea")));

        WeatherSource nea = source("nea");
        WeatherSource none = source("none");
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(nea, none));

        assertSame(nea, resolver.forCountry("sg").orElseThrow());
    }

    @Test
    void singaporeFallsBackToTheFixtureWhenNeaAndFallbackAreNotRegistered() {
        // Dev profile: regions.json says "nea"/"open-meteo", but only the fixture bean exists.
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("sg")).thenReturn(Optional.of(region("sg", "nea")));
        when(regions.fallback()).thenReturn(region(null, "open-meteo"));

        WeatherSource fixture = source("fixture");
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(fixture));

        assertSame(fixture, resolver.forCountry("sg").orElseThrow());
    }

    @Test
    void unknownCountryResolvesToTheFallbackProvider() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("zz")).thenReturn(Optional.empty());
        when(regions.fallback()).thenReturn(region(null, "open-meteo"));

        WeatherSource openMeteo = source("open-meteo");
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(openMeteo));

        assertSame(openMeteo, resolver.forCountry("zz").orElseThrow());
    }

    @Test
    void regionWithAnUnregisteredProviderFallsThroughToTheFallbackProvider() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("jp")).thenReturn(Optional.of(region("jp", "missing")));
        when(regions.fallback()).thenReturn(region(null, "open-meteo"));

        WeatherSource openMeteo = source("open-meteo");
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(openMeteo));

        assertSame(openMeteo, resolver.forCountry("jp").orElseThrow());
    }

    @Test
    void forPointFallsBackToTheFallbackProviderWhenNoRegionMatches() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byPoint(35.6762, 139.6503))
            .thenReturn(Optional.empty());
        when(regions.fallback()).thenReturn(region(null, "open-meteo"));

        WeatherSource openMeteo = source("open-meteo");
        when(openMeteo.covers(35.6762, 139.6503)).thenReturn(true);
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(openMeteo));

        assertSame(openMeteo, resolver.forPoint(35.6762, 139.6503).orElseThrow());
    }

    @Test
    void onlyNoneRegisteredMeansNoProvider() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("sg")).thenReturn(Optional.of(region("sg", "nea")));

        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(source("none")));

        assertTrue(resolver.forCountry("sg").isEmpty());
    }

    @Test
    void forPointOutsideSingaporeButInsideAnotherRegionIsEmpty() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byPoint(35.6762, 139.6503))
            .thenReturn(Optional.of(region("jp", "nea")));

        WeatherSource nea = source("nea");
        when(nea.covers(35.6762, 139.6503)).thenReturn(false);
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(nea));

        assertTrue(resolver.forPoint(35.6762, 139.6503).isEmpty());
    }

    @Test
    void forPointReturnsTheProviderWhenItCoversThePoint() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byPoint(1.3521, 103.8198))
            .thenReturn(Optional.of(region("sg", "nea")));

        WeatherSource nea = source("nea");
        when(nea.covers(1.3521, 103.8198)).thenReturn(true);
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(nea));

        assertSame(nea, resolver.forPoint(1.3521, 103.8198).orElseThrow());
    }

    @Test
    void duplicateProviderIdsFailFast() {
        RegionRegistry regions = mock(RegionRegistry.class);
        WeatherSource first = source("nea");
        WeatherSource second = source("nea");

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> new DefaultWeatherSourceResolver(regions, List.of(first, second)));

        assertTrue(error.getMessage().contains("Duplicate weather source id"));
    }

    private static WeatherSource source(String id) {
        WeatherSource source = mock(WeatherSource.class);
        when(source.id()).thenReturn(id);
        return source;
    }

    private static RegionProfile region(String code, String weatherProvider) {
        Map<String, String> providers = weatherProvider == null
            ? Map.of()
            : Map.of("weather", weatherProvider);

        return new RegionProfile(
            code,
            code,
            RegionTier.FULL,
            "en",
            null,
            ZoneId.of("UTC"),
            List.of(),
            providers,
            new TileConfig(null, null, "wgs84"));
    }
}
