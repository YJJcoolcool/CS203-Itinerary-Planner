package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.time.LocalDate;
import java.util.List;

public interface FlightLookupProvider {

    List<FlightRecord> findFlights(
        String flightNumber,
        LocalDate flightDate
    );
}