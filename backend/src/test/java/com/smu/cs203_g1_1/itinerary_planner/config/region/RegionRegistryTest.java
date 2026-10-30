package com.smu.cs203_g1_1.itinerary_planner.config.region;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionRegistryTest {

    private static final String FALLBACK = """
        { "tier": "partial", "locale": "en", "currency": null, "defaultZone": "UTC",
          "providers": {}, "tiles": { "crs": "wgs84" } }
        """;

    private static final String SG_BBOX =
        "{ \"minLat\": 1.15, \"maxLat\": 1.48, \"minLon\": 103.60, \"maxLon\": 104.10 }";

    private RegionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RegionRegistry();
    }

    @Test
    void shippedResourceLoadsSingaporeAsAFullRegion() {
        RegionProfile sg = registry.byCode("sg").orElseThrow();

        assertEquals("sg", sg.code());
        assertEquals("Singapore", sg.name());
        assertEquals(RegionTier.FULL, sg.tier());
        assertEquals(ZoneId.of("Asia/Singapore"), sg.zone());
        assertEquals("en-SG", sg.locale());
        assertEquals("SGD", sg.currency());
        assertEquals("nea", sg.provider(Capability.WEATHER).orElseThrow());
        assertEquals("onemap", sg.provider(Capability.ROUTING).orElseThrow());
        assertEquals("wgs84", sg.tiles().crs());
        assertEquals(1, registry.all().size());
    }

    @Test
    void codeLookupIsCaseInsensitiveAndUnknownCodesAreEmpty() {
        assertEquals("sg", registry.byCode("SG").orElseThrow().code());
        assertEquals("sg", registry.byCode("sg").orElseThrow().code());
        assertTrue(registry.byCode("my").isEmpty());
    }

    @Test
    void byPointResolvesInsideAndOnTheSingaporeBoundary() {
        assertEquals("sg", registry.byPoint(1.3521, 103.8198).orElseThrow().code());
        assertEquals("sg", registry.byPoint(1.15, 103.60).orElseThrow().code());
        assertTrue(registry.byPoint(3.1390, 101.6869).isEmpty(), "Kuala Lumpur is outside SG");
    }

    @Test
    void boundingBoxUsesInclusiveBounds() {
        BoundingBox box = new BoundingBox(1.15, 1.48, 103.60, 104.10);

        assertTrue(box.contains(1.3521, 103.8198));
        assertTrue(box.contains(1.15, 103.60));
        assertFalse(box.contains(3.1390, 101.6869));
    }

    @Test
    void fallbackIsParsedWithPartialTierAndUtc() {
        RegionProfile fallback = registry.fallback();

        assertEquals(RegionTier.PARTIAL, fallback.tier());
        assertEquals(ZoneId.of("UTC"), fallback.zone());
        assertEquals("en", fallback.locale());
        assertTrue(fallback.provider(Capability.WEATHER).isEmpty());
    }

    @Test
    void invertedBoundingBoxFailsFast() {
        String bbox =
            "{ \"minLat\": 1.48, \"maxLat\": 1.15, \"minLon\": 103.60, \"maxLon\": 104.10 }";

        assertThrows(IllegalStateException.class,
            () -> registryFrom(document(region("sg", "full", bbox, "wgs84"))));
    }

    @Test
    void nonWgs84BasemapFailsFast() {
        assertThrows(IllegalStateException.class,
            () -> registryFrom(document(region("sg", "full", SG_BBOX, "gcj02"))));
    }

    @Test
    void duplicateRegionCodeFailsFast() {
        String duplicated = region("sg", "full", SG_BBOX, "wgs84") + ","
            + region("SG", "partial", SG_BBOX, "wgs84");

        assertThrows(IllegalStateException.class, () -> registryFrom(document(duplicated)));
    }

    @Test
    void emptyRegionListFailsFast() {
        assertThrows(IllegalStateException.class,
            () -> registryFrom("{\"regions\":[],\"fallback\":" + FALLBACK + "}"));
    }

    private static RegionRegistry registryFrom(String json) {
        InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return new RegionRegistry(input);
    }

    private static String document(String regions) {
        return "{\"regions\":[" + regions + "],\"fallback\":" + FALLBACK + "}";
    }

    private static String region(String code, String tier, String bbox, String crs) {
        return """
            {
              "code": "%s",
              "name": "Test Region",
              "tier": "%s",
              "locale": "en",
              "currency": null,
              "defaultZone": "UTC",
              "bboxes": [%s],
              "providers": {},
              "tiles": { "url": "https://example.test/{z}/{x}/{y}.png",
                         "attribution": "test", "crs": "%s" }
            }
            """.formatted(code, tier, bbox, crs);
    }
}
