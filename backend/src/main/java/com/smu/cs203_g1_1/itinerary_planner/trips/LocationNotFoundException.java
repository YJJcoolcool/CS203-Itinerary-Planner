package com.smu.cs203_g1_1.itinerary_planner.trips;

public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(String id) {
        super("Location not found with id: " + id);
    }
}