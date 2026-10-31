package com.smu.cs203_g1_1.itinerary_planner.config.region;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable region descriptor loaded from {@code regions.json}.
 *
 * <p>The fallback profile has no code, name or bounding boxes; it only carries
 * the defaults used when a country is not configured.</p>
 */
public record RegionProfile(
    String code,
    String name,
    RegionTier tier,
    String locale,
    String currency,
    ZoneId zone,
    List<BoundingBox> bboxes,
    Map<String, String> providers,
    TileConfig tiles
) {

    public RegionProfile {
        bboxes = bboxes == null ? List.of() : List.copyOf(bboxes);
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    /**
     * Looks up the provider id configured for a capability. Provider ids are
     * intentionally not resolved to beans here; that is the provider catalog's
     * job.
     */
    public Optional<String> provider(Capability capability) {
        if (capability == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(capability.name().toLowerCase(Locale.ROOT)));
    }
}
