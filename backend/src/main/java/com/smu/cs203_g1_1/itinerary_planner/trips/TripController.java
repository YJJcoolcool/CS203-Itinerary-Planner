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

    public record TripRequest(String name, LocalDate startDate, LocalDate endDate, String ownerId) { }

    public record TripUpdateRequest(String name, LocalDate startDate, LocalDate endDate) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip createTrip(@RequestBody TripRequest request) {
        return tripService.createTrip( request.name(), request.startDate(), request.endDate(), request.ownerId()); }

    @GetMapping
    public List<Trip> getAllTrips() {
        return tripService.getAllTrips();
    }

    @GetMapping("/{tripId}")
    public Trip getTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId) {
        return tripService.getTripForMember(tripId, userService.getCurrentUserId(jwt));
    }

    @PutMapping("/{tripId}")
    public Trip updateTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @RequestBody TripUpdateRequest request) {
        return tripService.updateTrip(userService.getCurrentUserId(jwt), tripId, request.name(), request.startDate(), request.endDate());
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId) {
        tripService.deleteTrip(userService.getCurrentUserId(jwt), tripId);
    }

    @PostMapping("/{tripId}/members/{userId}")
    public Trip addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @PathVariable("userId") String userId) {
        return tripService.addMember(userService.getCurrentUserId(jwt), tripId, userId);
    }

    @DeleteMapping("/{tripId}/members/{userId}")
    public Trip removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("tripId") String tripId, @PathVariable("userId") String userId) {
        return tripService.removeMember(userService.getCurrentUserId(jwt), tripId, userId);
    }
}
