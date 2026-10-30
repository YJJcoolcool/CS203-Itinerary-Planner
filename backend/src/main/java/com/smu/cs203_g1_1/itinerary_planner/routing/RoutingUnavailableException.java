package com.smu.cs203_g1_1.itinerary_planner.routing;

/**
 * Raised when the trip's region has no routing provider for Phase 1.
 */
public class RoutingUnavailableException extends RuntimeException {

    private final String countryCode;

    public RoutingUnavailableException(String countryCode) {
        super("Routing is not available for country " + countryCode);
        this.countryCode = countryCode;
    }

    public String getCountryCode() {
        return countryCode;
    }
}
