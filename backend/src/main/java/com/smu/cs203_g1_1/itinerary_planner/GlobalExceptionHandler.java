package com.smu.cs203_g1_1.itinerary_planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.smu.cs203_g1_1.itinerary_planner.geo.ApiError;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoBadRequestException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoUnavailableException;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<Void> handleLocationNotFound(LocationNotFoundException e) {
        log.warn("Location not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }

    /** Maps invalid geo/POI input to 400 with a {@code BAD_REQUEST} envelope. */
    @ExceptionHandler(GeoBadRequestException.class)
    public ResponseEntity<ApiError> handleGeoBadRequest(GeoBadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", e.getMessage()));
    }

    /** Maps a missing geo result to 404 with a {@code NOT_FOUND} envelope. */
    @ExceptionHandler(GeoNotFoundException.class)
    public ResponseEntity<ApiError> handleGeoNotFound(GeoNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getMessage()));
    }

    /** Maps an OSM upstream failure to 503 with an {@code UPSTREAM_UNAVAILABLE} envelope. */
    @ExceptionHandler(GeoUnavailableException.class)
    public ResponseEntity<ApiError> handleGeoUnavailable(GeoUnavailableException e) {
        log.warn("Geocoding service unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("UPSTREAM_UNAVAILABLE", e.getMessage()));
    }
}