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
 *   <li>the region's {@code providers.weather} entry, when that id is
 *       registered;</li>
 *   <li>otherwise the single registered provider whose id is not
 *       {@code "none"} (the global default);</li>
 *   <li>otherwise no provider.</li>
 * </ol>
 *
 * <p>The global fallback keeps the dev fixture working: in dev the SG region
 * still names {@code "nea"}, but only the fixture bean is registered.</p>
 *
 * <p>Unknown provider ids in {@code regions.json} are deliberately tolerated
 * here — they fall through to the global default. Duplicate bean ids are a
 * configuration bug and fail startup.</p>
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
        return regionRegistry.byCode(countryCode)
            .flatMap(this::resolveForRegion);
    }

    @Override
    public Optional<WeatherSource> forPoint(double latitude, double longitude) {
        return regionRegistry.byPoint(latitude, longitude)
            .flatMap(this::resolveForRegion)
            .filter(source -> source.covers(latitude, longitude));
    }

    private Optional<WeatherSource> resolveForRegion(RegionProfile region) {
        Optional<String> configuredId = region.provider(Capability.WEATHER);

        if (configuredId.isPresent()) {
            WeatherSource configured = byId.get(configuredId.get());
            if (configured != null) {
                return Optional.of(configured);
            }
        }

        return Optional.ofNullable(global);
    }
}
