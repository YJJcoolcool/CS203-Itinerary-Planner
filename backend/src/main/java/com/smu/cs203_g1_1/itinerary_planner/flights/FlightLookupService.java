package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;

@Service
public class FlightLookupService {

    private final FlightLookupProvider provider;
    private final TripService tripService;

    public FlightLookupService(FlightLookupProvider provider, TripService tripService) {
        this.provider = provider;
        this.tripService = tripService;
    }

    public FlightLookupResponse lookup(
            String userId,
            String tripId,
            String flightNumber,
            LocalDate flightDate) {

        // Only trip editors and owners can perform this operation.
        tripService.requireTripRole(
                tripId,
                userId,
                TripRole.EDITOR);

        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Flight number is required");
        }

        if (flightDate == null) {
            throw new IllegalArgumentException(
                    "Flight date is required");
        }

        String normalizedNumber = flightNumber
                .trim()
                .toUpperCase();

        List<FlightRecord> flights = provider.findFlights(
                normalizedNumber,
                flightDate);

        if (flights.isEmpty()) {
            return new FlightLookupResponse(
                    "NOT_FOUND",
                    "No matching flight was found.",
                    List.of());
        }

        if (flights.size() > 1) {
            return new FlightLookupResponse(
                    "AMBIGUOUS",
                    "Multiple matching flights were found. Please select one.",
                    flights);
        }

        FlightRecord flight = flights.get(0);

        if (flight.departureTime() == null
                || flight.arrivalTime() == null) {

            return new FlightLookupResponse(
                    "NOT_FOUND",
                    "Flight information is incomplete.",
                    List.of());
        }

        return new FlightLookupResponse(
                "SUCCESS",
                "Flight information retrieved successfully.",
                flights);
    }
}