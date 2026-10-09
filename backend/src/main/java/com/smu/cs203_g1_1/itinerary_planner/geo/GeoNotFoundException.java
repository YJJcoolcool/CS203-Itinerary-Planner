package com.smu.cs203_g1_1.itinerary_planner.geo;

/** Thrown when no place matches a lookup; mapped to HTTP 404. */
public class GeoNotFoundException extends RuntimeException {

    public GeoNotFoundException(String message) {
        super(message);
    }
}
