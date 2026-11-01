package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

import com.smu.cs203_g1_1.itinerary_planner.geo.GeoBadRequestException;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

/**
 * Endpoints for stored locations: shared places (seeded or imported from
 * OpenStreetMap) and places users enter by hand, which are private to the
 * trip they were created in (CGT-142).
 */
@RestController
@RequestMapping("/api")
public class LocationController {
    private final LocationService locationService;
    private final UserService userService;

    public LocationController(LocationService locationService, UserService userService) {
        this.locationService = locationService;
        this.userService = userService;
    }

    /** Request body for OSM import: the identity to resolve ({@code osmType} plus {@code osmId}). */
    public record FromOsmRequest(String osmType, Long osmId) {}

    /** Shared places only; trip-private places are never listed. */
    @GetMapping("/locations")
    public List<Location> getSharedLocations() {
        return locationService.getSharedLocations();
    }

    @GetMapping("/locations/{id}")
    public Location getLocationById(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return locationService.getVisibleLocation(id, userService.getCurrentUserId(jwt));
    }

    /** Adds a place by hand to a trip (EDITOR or above); only that trip can see or use it. */
    @Operation(summary = "Create a manual location, private to the trip")
    @PostMapping("/trips/{tripId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public Location createManualLocation(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable("tripId") String tripId,
                                         @RequestBody ManualLocationRequest request) {
        return locationService.createManualLocation(tripId, userService.getCurrentUserId(jwt), request);
    }

    /**
     * Imports an OSM place as a stored location, returning the existing row when it was
     * already imported (idempotent, always HTTP 200).
     *
     * @throws GeoBadRequestException when {@code osmType} or {@code osmId} is missing
     */
    @Operation(summary = "Import an OpenStreetMap place as a Location")
    @PostMapping("/locations/from-osm")
    public ResponseEntity<Location> importFromOsm(@RequestBody FromOsmRequest request) {
        if (request == null
                || request.osmType() == null || request.osmType().isBlank()
                || request.osmId() == null) {
            throw new GeoBadRequestException("Both 'osmType' and 'osmId' are required");
        }
        Location location = locationService.findOrCreateFromOsm(request.osmType().trim(), request.osmId());
        return ResponseEntity.ok(location);
    }

    /** Deletes a trip-private place (EDITOR or above on its trip). Shared places can't be deleted. */
    @DeleteMapping("/locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocation(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        locationService.deleteLocation(id, userService.getCurrentUserId(jwt));
    }
}
