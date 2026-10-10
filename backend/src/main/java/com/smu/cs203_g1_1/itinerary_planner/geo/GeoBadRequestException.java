package com.smu.cs203_g1_1.itinerary_planner.geo;

/** Thrown for invalid geocoding or POI input; mapped to HTTP 400. */
public class GeoBadRequestException extends RuntimeException {

    public GeoBadRequestException(String message) {
        super(message);
    }
}
