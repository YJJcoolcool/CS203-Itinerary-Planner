package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.util.List;

public record FlightLookupResponse(
        String status,
        String message,
        List<FlightRecord> flights
) {}