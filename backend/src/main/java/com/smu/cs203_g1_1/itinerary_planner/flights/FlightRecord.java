package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.time.ZonedDateTime;

public record FlightRecord(
    String flightNumber,
    String airline,

    String departureAirportCode,
    String departureAirportName,
    String departureTerminal,
    String departureTimeZone,

    String arrivalAirportCode,
    String arrivalAirportName,
    String arrivalTerminal,
    String arrivalTimeZone,

    ZonedDateTime departureTime,
    ZonedDateTime arrivalTime
) {}