package com.smu.cs203_g1_1.itinerary_planner.flights;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
@RequestMapping("/api/trips/{tripId}/flights")
public class FlightLookupController {

    private final FlightLookupService flightLookupService;
    private final UserService userService;

    public FlightLookupController(
            FlightLookupService flightLookupService,
            UserService userService) {
        this.flightLookupService = flightLookupService;
        this.userService = userService;
    }

    public record FlightLookupRequest(
            String flightNumber,
            LocalDate flightDate
    ) {}

    @PostMapping("/lookup")
    @ResponseStatus(HttpStatus.OK)
    public FlightLookupResponse lookup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tripId,
            @RequestBody FlightLookupRequest request) {

        return flightLookupService.lookup(
                userService.getCurrentUserId(jwt),
                tripId,
                request.flightNumber(),
                request.flightDate());
    }
}