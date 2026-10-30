package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.smu.cs203_g1_1.itinerary_planner.config.region.Capability;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;

import org.springframework.stereotype.Component;

/**
 * Region-first weather provider lookup.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>the matched region's {@code providers.weather} entry, when that id is
 *       registered;</li>
 *   <li>otherwise the fallback region's {@code providers.weather} entry, when
 *       that id is registered;</li>
 *   <li>otherwise the single registered provider whose id is not
 *       {@code "none"} (the global default);</li>
 *   <li>otherwise no provider.</li>
 * </ol>
 *
 * <p>The fallback and global steps keep non-SG visits and the dev fixture
 * working: unknown countries resolve to the fallback's Open-Meteo provider,
 * while dev (only the fixture registered) still resolves to the fixture.</p>
 *
 * <p>Unknown provider ids in {@code regions.json} are deliberately tolerated
 * here — they fall through. Duplicate bean ids are a configuration bug and
 * fail startup.</p>
 */
@Component
public class DefaultWeatherSourceResolver implements WeatherSourceResolver {

    private static final String NO_PROVIDER = "none";

    private final RegionRegistry regionRegistry;
    private final Map<String, WeatherSource> byId;
    private final WeatherSource global;

    public DefaultWeatherSourceResolver(RegionRegistry regionRegistry,
                                        List<WeatherSource> weatherSources) {
        this.regionRegistry = regionRegistry;

        Map<String, WeatherSource> index = new LinkedHashMap<>();
        for (WeatherSource source : weatherSources) {
            String id = source.id();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                    "WeatherSource id must not be blank: " + source.getClass().getName());
            }
            if (index.putIfAbsent(id, source) != null) {
                throw new IllegalStateException("Duplicate weather source id: " + id);
            }
        }

        this.byId = Map.copyOf(index);
        this.global = index.values().stream()
            .filter(source -> !NO_PROVIDER.equals(source.id()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Optional<WeatherSource> forCountry(String countryCode) {
        RegionProfile region = regionRegistry.byCode(countryCode)
            .orElseGet(this::fallbackRegion);
        return resolveForRegion(region);
    }

    @Override
    public Optional<WeatherSource> forPoint(double latitude, double longitude) {
        RegionProfile region = regionRegistry.byPoint(latitude, longitude)
            .orElseGet(this::fallbackRegion);
        return resolveForRegion(region)
            .filter(source -> source.covers(latitude, longitude));
    }

    private Optional<WeatherSource> resolveForRegion(RegionProfile region) {
        Optional<WeatherSource> configured = registeredWeatherSource(region);
        if (configured.isPresent()) {
            return configured;
        }

        Optional<WeatherSource> fromFallback = registeredWeatherSource(fallbackRegion());
        if (fromFallback.isPresent()) {
            return fromFallback;
        }

        return Optional.ofNullable(global);
    }

    private Optional<WeatherSource> registeredWeatherSource(RegionProfile region) {
        if (region == null) {
            return Optional.empty();
        }
        return region.provider(Capability.WEATHER)
            .map(byId::get)
            .filter(source -> source != null);
    }

    private RegionProfile fallbackRegion() {
        return regionRegistry.fallback();
    }
}
