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

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final UserService userService;

    public TripController(TripService tripService, UserService userService) {
        this.tripService = tripService;
        this.userService = userService;
    }

    public record TripRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String countryCode,
        String coverImageUrl
    ) {}

    public record TripUpdateRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String countryCode,
        String coverImageUrl
    ) {}

    // role is optional when adding a member (defaults to VIEWER)
    public record MemberRequest(TripRole role) { }

    // CGT-168: the member who takes over the trip
    public record OwnerTransferRequest(String userId) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip createTrip(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TripRequest request) {

        return tripService.createTrip(
            request.name(),
            request.startDate(),
            request.endDate(),
            userService.getCurrentUserId(jwt),
            request.countryCode(),
            request.coverImageUrl()
        );
    }

    @GetMapping
    public List<Trip> getTrips(@AuthenticationPrincipal Jwt jwt) {
        return userService.findCurrentUser(jwt)
                .map(user -> tripService.getTripsForUser(user.getId()))
                .orElseGet(List::of);
    }

    @GetMapping("/{tripId}")
    public Trip getTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId) {
        return tripService.getTripForMember(tripId, userService.getCurrentUserId(jwt));
    }

    @PutMapping("/{tripId}")
    public Trip updateTrip(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @RequestBody TripUpdateRequest request) {

        return tripService.updateTrip(
            userService.getCurrentUserId(jwt),
            tripId,
            request.name(),
            request.startDate(),
            request.endDate(),
            request.countryCode(),
            request.coverImageUrl()
        );
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId) {
        tripService.deleteTrip(userService.getCurrentUserId(jwt), tripId);
    }

    @PostMapping("/{tripId}/members/{userId}")
    public Trip addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @PathVariable("userId") String userId, @RequestBody(required = false) MemberRequest request) {
        return tripService.addMember(userService.getCurrentUserId(jwt), tripId, userId, request == null ? null : request.role());
    }

    @PutMapping("/{tripId}/members/{userId}")
    public Trip changeMemberRole(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @PathVariable("userId") String userId, @RequestBody MemberRequest request) {
        return tripService.changeMemberRole(userService.getCurrentUserId(jwt), tripId, userId, request.role());
    }

    // Owner removes a member, or a member removes themselves to leave the trip (CGT-167)
    @DeleteMapping("/{tripId}/members/{userId}")
    public Trip removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @PathVariable("userId") String userId) {
        return tripService.removeMember(userService.getCurrentUserId(jwt), tripId, userId);
    }

    // CGT-168: owner hands the trip to another member
    @PutMapping("/{tripId}/owner")
    public Trip transferOwnership(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @RequestBody OwnerTransferRequest request) {
        return tripService.transferOwnership(userService.getCurrentUserId(jwt), tripId, request == null ? null : request.userId());
    }
}