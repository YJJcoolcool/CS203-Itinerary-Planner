package com.smu.cs203_g1_1.itinerary_planner.routing;

public enum RouteMode {

    PUBLIC_TRANSPORT("pt"),
    DRIVING("drive"),
    WALKING("walk"),
    CYCLING("cycle");

    private final String oneMapValue;

    RouteMode(String oneMapValue) {
        this.oneMapValue = oneMapValue;
    }

    public String getOneMapValue() {
        return oneMapValue;
    }
}