package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smu.cs203_g1_1.itinerary_planner.config.region.Capability;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.config.region.TileConfig;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

/**
 * Read-only region capability catalog for the trip UI.
 *
 * <p>Configured regions are returned first, followed by the synthetic
 * {@code "*"} fallback so a client can answer "can this country do weather?"
 * for any ISO code. Bounding boxes stay server-side: the client only needs
 * identity, capabilities and basemap.</p>
 */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionRegistry regionRegistry;
    private final WeatherSourceResolver weatherSourceResolver;

    public RegionController(RegionRegistry regionRegistry,
                            WeatherSourceResolver weatherSourceResolver) {
        this.regionRegistry = regionRegistry;
        this.weatherSourceResolver = weatherSourceResolver;
    }

    @GetMapping
    public List<RegionResponse> list() {
        List<RegionResponse> responses = new ArrayList<>(regionRegistry.all().stream()
            .map(this::toResponse)
            .toList());
        responses.add(toResponse(regionRegistry.fallback(), "*"));
        return List.copyOf(responses);
    }

    private RegionResponse toResponse(RegionProfile region) {
        return toResponse(region, region.code());
    }

    private RegionResponse toResponse(RegionProfile region, String code) {
        return new RegionResponse(
            code,
            region.name(),
            region.tier().name().toLowerCase(Locale.ROOT),
            region.locale(),
            region.currency(),
            region.zone().getId(),
            capabilities(region),
            tiles(region.tiles())
        );
    }

    private List<String> capabilities(RegionProfile region) {
        Optional<WeatherSource> resolved = weatherSourceResolver.forRegion(region);

        return Arrays.stream(Capability.values())
            .filter(capability -> supports(capability, region, resolved))
            .map(capability -> capability.name().toLowerCase(Locale.ROOT))
            .toList();
    }

    private static boolean supports(Capability capability,
                                    RegionProfile region,
                                    Optional<WeatherSource> resolved) {
        return switch (capability) {
            case WEATHER -> resolved.isPresent();
            case FORECAST -> resolved
                .map(source -> "open-meteo".equals(source.id()))
                .orElse(false);
            case ROUTING -> region.provider(Capability.ROUTING).isPresent();
        };
    }

    private static TileResponse tiles(TileConfig tiles) {
        if (tiles == null) {
            return null;
        }
        return new TileResponse(tiles.url(), tiles.attribution(), tiles.crs());
    }

    public record RegionResponse(
        String code,
        String name,
        String tier,
        String locale,
        String currency,
        String defaultZone,
        List<String> capabilities,
        TileResponse tiles
    ) {
    }

    public record TileResponse(String url, String attribution, String crs) {
    }
}
