package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

import com.smu.cs203_g1_1.itinerary_planner.geo.GeoBadRequestException;

/**
 * CRUD endpoints for stored locations, plus import of an OpenStreetMap place by identity.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /** Request body for OSM import: the identity to resolve ({@code osmType} plus {@code osmId}). */
    public record FromOsmRequest(String osmType, Long osmId) {}

    @GetMapping
    public List<Location> getAllLocations() {
        return locationService.getAllLocations();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocationById(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    @PostMapping
    public Location createLocation(@RequestBody Location location) {
        return locationService.createLocation(location);
    }

    /**
     * Imports an OSM place as a stored location, returning the existing row when it was
     * already imported (idempotent, always HTTP 200).
     *
     * @throws GeoBadRequestException when {@code osmType} or {@code osmId} is missing
     */
    @Operation(summary = "Import an OpenStreetMap place as a Location")
    @PostMapping("/from-osm")
    public ResponseEntity<Location> importFromOsm(@RequestBody FromOsmRequest request) {
        if (request == null
                || request.osmType() == null || request.osmType().isBlank()
                || request.osmId() == null) {
            throw new GeoBadRequestException("Both 'osmType' and 'osmId' are required");
        }
        Location location = locationService.findOrCreateFromOsm(request.osmType().trim(), request.osmId());
        return ResponseEntity.ok(location);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLocation(@PathVariable Long id) {
        if (locationService.deleteLocation(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
