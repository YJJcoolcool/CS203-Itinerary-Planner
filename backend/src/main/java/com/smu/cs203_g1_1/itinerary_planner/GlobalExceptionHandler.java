package com.smu.cs203_g1_1.itinerary_planner;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.geo.ApiError;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoBadRequestException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoUnavailableException;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.user.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<Void> handleLocationNotFound(LocationNotFoundException e) {
        log.warn("Location not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }

    // Expose the reason we wrote in ResponseStatusException; Spring hides it by default.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
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
