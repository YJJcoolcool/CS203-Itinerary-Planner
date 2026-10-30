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
    void singaporeFallsBackToTheFixtureWhenNeaIsNotRegistered() {
        // Dev profile: regions.json still says "nea", but only the fixture bean exists.
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("sg")).thenReturn(Optional.of(region("sg", "nea")));

        WeatherSource fixture = source("fixture");
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(fixture));

        assertSame(fixture, resolver.forCountry("sg").orElseThrow());
    }

    @Test
    void unknownCountryHasNoProvider() {
        RegionRegistry regions = mock(RegionRegistry.class);
        when(regions.byCode("zz")).thenReturn(Optional.empty());

        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            regions, List.of(source("nea")));

        assertTrue(resolver.forCountry("zz").isEmpty());
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
