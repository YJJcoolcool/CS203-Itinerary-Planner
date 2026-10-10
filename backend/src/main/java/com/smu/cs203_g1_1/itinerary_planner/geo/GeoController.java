package com.smu.cs203_g1_1.itinerary_planner.geo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.SearchResponse;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.SinglePlaceResponse;

/**
 * REST endpoints for geocoding search, reverse geocoding, and nearby POI discovery.
 * Invalid input and upstream failures are raised as exceptions rendered by the global
 * exception handler as 400/404/503 envelopes.
 */
@RestController
@RequestMapping("/api/geo")
@Tag(name = "Geocoding & POI", description = "Search, reverse geocode, and discover points of interest via OpenStreetMap (Nominatim & Overpass).")
public class GeoController {

    private final GeoService geoService;
    private final PoiService poiService;

    public GeoController(GeoService geoService, PoiService poiService) {
        this.geoService = geoService;
        this.poiService = poiService;
    }

    /**
     * Forward-geocodes a free-text query, clamping {@code limit} to 1..10 and normalising
     * blank country codes to null.
     *
     * @throws GeoBadRequestException when {@code q} is null or blank
     */
    @Operation(summary = "Search for places by free-text query")
    @GetMapping("/search")
    public SearchResponse search(
            @Parameter(description = "Free-text place query, e.g. 'Marina Bay Sands'") @RequestParam("q") String q,
            @Parameter(description = "Maximum number of results (1-10, default 5)") @RequestParam(value = "limit", defaultValue = "5") int limit,
            @Parameter(description = "Optional comma-separated ISO country codes to restrict results, e.g. 'sg'") @RequestParam(value = "countrycodes", required = false) String countryCodes,
            @Parameter(description = "Optional bounding box 'minLon,minLat,maxLon,maxLat' used to bias results") @RequestParam(value = "viewbox", required = false) String viewBox,
            @Parameter(description = "Whether to restrict results to the supplied viewbox") @RequestParam(value = "bounded", defaultValue = "false") boolean bounded) {
        if (q == null || q.isBlank()) {
            throw new GeoBadRequestException("Query parameter 'q' must not be blank");
        }
        String normalizedCountryCodes = countryCodes == null ? null : countryCodes.toLowerCase().trim();
        if (normalizedCountryCodes != null && normalizedCountryCodes.isEmpty()) {
            normalizedCountryCodes = null;
        }
        int clampedLimit = Math.max(1, Math.min(10, limit));
        return new SearchResponse(
                geoService.search(q.trim(), clampedLimit, normalizedCountryCodes, viewBox, bounded),
                OsmAttribution.TEXT);
    }

    /**
     * Reverse-geocodes coordinates to the nearest place, clamping {@code zoom} to 3..18.
     *
     * @throws GeoBadRequestException when coordinates are out of range
     * @throws GeoNotFoundException  when no place matches the coordinates
     */
    @Operation(summary = "Reverse geocode coordinates to the nearest place")
    @GetMapping("/reverse")
    public SinglePlaceResponse reverse(
            @Parameter(description = "Latitude in decimal degrees (-90 to 90)") @RequestParam("lat") double lat,
            @Parameter(description = "Longitude in decimal degrees (-180 to 180)") @RequestParam("lon") double lon,
            @Parameter(description = "Nominatim zoom level (3-18, default 18)") @RequestParam(value = "zoom", defaultValue = "18") int zoom) {
        validateCoordinate(lat, lon);
        int clampedZoom = Math.max(3, Math.min(18, zoom));
        PlaceDto place = geoService.reverse(lat, lon, clampedZoom);
        return new SinglePlaceResponse(place, OsmAttribution.TEXT);
    }

    /**
     * Finds nearby whitelisted POIs, clamping {@code radius} to 50..2000 and {@code limit}
     * to 1..200.
     *
     * @throws GeoBadRequestException for out-of-range coordinates or an unknown category
     */
    @Operation(summary = "Find nearby points of interest in a whitelisted category")
    @GetMapping("/pois")
    public SearchResponse pois(
            @Parameter(description = "Latitude in decimal degrees (-90 to 90)") @RequestParam("lat") double lat,
            @Parameter(description = "Longitude in decimal degrees (-180 to 180)") @RequestParam("lon") double lon,
            @Parameter(description = "Search radius in metres (50-2000, default 500)") @RequestParam(value = "radius", defaultValue = "500") int radius,
            @Parameter(description = "POI category: restaurant, cafe, bar, fast_food, hotel, hostel, attraction, museum") @RequestParam(value = "category", defaultValue = "restaurant") String category,
            @Parameter(description = "Maximum number of results (1-200, default 50)") @RequestParam(value = "limit", defaultValue = "50") int limit) {
        validateCoordinate(lat, lon);
        int effectiveRadius = Math.min(2000, Math.max(50, radius));
        int effectiveLimit = Math.min(200, Math.max(1, limit));
        return new SearchResponse(
                poiService.findPois(lat, lon, effectiveRadius, category, effectiveLimit),
                OsmAttribution.TEXT);
    }

    /**
     * Validates latitude/longitude ranges.
     *
     * @throws GeoBadRequestException when a coordinate is outside its valid range
     */
    private static void validateCoordinate(double lat, double lon) {
        if (lat < -90 || lat > 90) {
            throw new GeoBadRequestException("Latitude must be between -90 and 90");
        }
        if (lon < -180 || lon > 180) {
            throw new GeoBadRequestException("Longitude must be between -180 and 180");
        }
    }
}
