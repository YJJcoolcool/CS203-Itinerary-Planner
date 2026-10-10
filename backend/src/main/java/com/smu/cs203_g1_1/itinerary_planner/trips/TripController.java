package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // ownerId removed: the owner is always the authenticated user.
    public record TripRequest(String name, LocalDate startDate, LocalDate endDate) { }

    public record TripUpdateRequest(String name, LocalDate startDate, LocalDate endDate) { }

    public record MemberRequest(TripRole role) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip createTrip(@AuthenticationPrincipal Jwt jwt, @RequestBody TripRequest request) {
        return tripService.createTrip(request.name(), request.startDate(), request.endDate(), jwt.getSubject());
    }

    // Returns only the trips the caller is a member of.
    @GetMapping
    public List<Trip> getMyTrips(@AuthenticationPrincipal Jwt jwt) {
        return tripService.getTripsForUser(jwt.getSubject());
    }

    @GetMapping("/{tripId}")
    public Trip getTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") Long tripId) {
        return tripService.getTrip(tripId, jwt.getSubject());
    }

    @PutMapping("/{tripId}")
    public Trip updateTrip(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @RequestBody TripUpdateRequest request) {

        return tripService.updateTrip(tripId, jwt.getSubject(), request.name(), request.startDate(), request.endDate());
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") Long tripId) {
        tripService.deleteTrip(tripId, jwt.getSubject());
    }

    // Body is optional; the role defaults to VIEWER.
    @PostMapping("/{tripId}/members/{userId}")
    public Trip addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) MemberRequest request) {

        return tripService.addMember(tripId, jwt.getSubject(), userId, request == null ? null : request.role());
    }

    @PutMapping("/{tripId}/members/{userId}")
    public Trip changeMemberRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("userId") Long userId,
            @RequestBody MemberRequest request) {

        return tripService.changeMemberRole(tripId, jwt.getSubject(), userId, request.role());
    }

    @DeleteMapping("/{tripId}/members/{userId}")
    public Trip removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("userId") Long userId) {

        return tripService.removeMember(tripId, jwt.getSubject(), userId);
    }
}