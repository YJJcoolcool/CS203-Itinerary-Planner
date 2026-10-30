package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smu.cs203_g1_1.itinerary_planner.config.region.Capability;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.config.region.TileConfig;

/**
 * Read-only region capability catalog for the trip UI.
 *
 * <p>Only configured regions are returned; the fallback profile is an internal
 * default and never appears as a selectable country. Bounding boxes stay
 * server-side: the client only needs identity, capabilities and basemap.</p>
 */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionRegistry regionRegistry;

    public RegionController(RegionRegistry regionRegistry) {
        this.regionRegistry = regionRegistry;
    }

    @GetMapping
    public List<RegionResponse> list() {
        return regionRegistry.all().stream()
            .map(RegionController::toResponse)
            .toList();
    }

    private static RegionResponse toResponse(RegionProfile region) {
        return new RegionResponse(
            region.code(),
            region.name(),
            region.tier().name().toLowerCase(Locale.ROOT),
            region.locale(),
            region.currency(),
            region.zone().getId(),
            capabilities(region),
            tiles(region.tiles())
        );
    }

    private static List<String> capabilities(RegionProfile region) {
        return Arrays.stream(Capability.values())
            .filter(capability -> region.provider(capability)
                .filter(id -> !id.isBlank())
                .isPresent())
            .map(capability -> capability.name().toLowerCase(Locale.ROOT))
            .toList();
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
