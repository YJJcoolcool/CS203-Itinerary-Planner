package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;

/**
 * Resolves the weather provider a trip or point should use.
 *
 * <p>Absence is explicit: an empty result means "no provider covers this
 * place", never "the forecast says dry". Callers use this to skip weather-driven
 * work instead of asking a provider that cannot answer.</p>
 */
public interface WeatherSourceResolver {

    Optional<WeatherSource> forCountry(String countryCode);

    Optional<WeatherSource> forPoint(double latitude, double longitude);

    /** Resolves the provider for a region using the shared fallback rules. */
    Optional<WeatherSource> forRegion(RegionProfile region);
}
