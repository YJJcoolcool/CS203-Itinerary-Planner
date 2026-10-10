package com.smu.cs203_g1_1.itinerary_planner.trips;

public enum TripRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean atLeast(TripRole required) {
        return this.compareTo(required) >= 0;
    }
}
