package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.time.ZonedDateTime;

public record FlightRecord(
    String flightNumber,
    String airline,

    String departureAirportCode,
    String departureAirportName,
    String departureTerminal,

    String arrivalAirportCode,
    String arrivalAirportName,
    String arrivalTerminal,

    ZonedDateTime departureTime,
    ZonedDateTime arrivalTime
) {}