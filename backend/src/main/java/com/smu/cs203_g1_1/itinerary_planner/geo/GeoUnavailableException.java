package com.smu.cs203_g1_1.itinerary_planner.geo;

/** Thrown when an OSM upstream call fails; mapped to HTTP 503. */
public class GeoUnavailableException extends RuntimeException {

    public GeoUnavailableException(String message) {
        super(message);
    }
}
