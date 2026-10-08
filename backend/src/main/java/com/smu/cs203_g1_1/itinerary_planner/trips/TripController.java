package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
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
    public Trip getTrip(@PathVariable("tripId") String tripId) {
        return tripService.getTrip(tripId);
    }

    @PutMapping("/{tripId}")
    public Trip updateTrip( @PathVariable("tripId") String tripId, @RequestBody TripUpdateRequest request) {

        return tripService.updateTrip( tripId, request.name(), request.startDate(), request.endDate());
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@PathVariable("tripId") String tripId) {
        tripService.deleteTrip(tripId);
    }

    @PostMapping("/{tripId}/members/{userId}")
    public Trip addMember( @PathVariable("tripId") String tripId, @PathVariable("userId") String userId) {

        return tripService.addMember(tripId, userId);
    }

    @DeleteMapping("/{tripId}/members/{userId}")
    public Trip removeMember( @PathVariable("tripId") String tripId, @PathVariable("userId") String userId) {

        return tripService.removeMember(tripId, userId);
    }
}
