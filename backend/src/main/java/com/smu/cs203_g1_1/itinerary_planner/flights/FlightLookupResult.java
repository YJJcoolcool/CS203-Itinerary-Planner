package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.util.List;

public record FlightLookupResult(
        String flightNumber,
        String flightDate,
        String airline,
        String departureAirport,
        String arrivalAirport,
        String departureTerminal,
        String arrivalTerminal,
        String departureDateTime,
        String arrivalDateTime,
        String status,
        List<Source> sources
) {
    public record Source(
            String title,
            String url
    ) {}
}