package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.ZonedDateTime;
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
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.location.Location;
import com.smu.cs203_g1_1.itinerary_planner.location.LocationRepository;


@RestController
@RequestMapping("/api/trips/{tripId}/activities")
public class ActivityController {

    private final ActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final LocationRepository locationRepository;

    public ActivityController( ActivityRepository activityRepository, TripRepository tripRepository, LocationRepository locationRepository) {

        this.activityRepository = activityRepository;
        this.tripRepository = tripRepository;
        this.locationRepository = locationRepository;
    }


    /*
     * Object expected from the frontend.
     *
     */
    public record ActivityRequest( String name, ZonedDateTime startDateTime, ZonedDateTime endDateTime, String status, Long locationId) {
    }


    /*
     * GET /api/trips/5/activities
     *
     * Get the complete itinerary for Trip 5.
     */
    @GetMapping
    public List<Activity> getActivities( @PathVariable("tripId") Long tripId) {

        checkTripExists(tripId);
        return activityRepository
                .findByTripIdOrderByStartDateTimeAsc(tripId);
    }


    /*
     * GET /api/trips/5/activities/10
     *
     * Get Activity 10 belonging to Trip 5.
     */
    @GetMapping("/{activityId}")
    public Activity getActivity( @PathVariable("tripId") Long tripId, @PathVariable("activityId") Long activityId) {

        return findActivity(tripId, activityId);
    }


    /*
     * POST /api/trips/5/activities
     *
     * Create an activity belonging to Trip 5.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Activity createActivity( @PathVariable("tripId") Long tripId, @RequestBody ActivityRequest request) {

        Trip trip = tripRepository
                .findById(tripId)
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND, "Trip not found"));

        Location location = locationRepository
                .findById(request.locationId())
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND, "Location not found"));

        validateTimes(
                request.startDateTime(),
                request.endDateTime());

        Activity activity = new Activity( request.name(), location, request.startDateTime(), request.endDateTime(), request.status());

        /*
         * This is the important relationship:
         *
         * Activity belongs to Trip.
         */
        activity.setTrip(trip);

        return activityRepository.save(activity);
    }


    /*
     * PUT /api/trips/5/activities/10
     */
    @PutMapping("/{activityId}")
    public Activity updateActivity( @PathVariable("tripId") Long tripId, @PathVariable("activityId") Long activityId, @RequestBody ActivityRequest request) {

        Activity activity = findActivity(tripId, activityId);

        Location location = locationRepository
                .findById(request.locationId())
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND, "Location not found"));

        validateTimes( request.startDateTime(), request.endDateTime());

        activity.setName(request.name());
        activity.setStartDateTime( request.startDateTime());
        activity.setEndDateTime( request.endDateTime());
        activity.setStatus( request.status());
        activity.setLocation(location);

        return activityRepository.save(activity);
    }


    /*
     * DELETE /api/trips/5/activities/10
     */
    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity( @PathVariable("tripId") Long tripId, @PathVariable("activityId") Long activityId) {

        Activity activity = findActivity(tripId, activityId);
        activityRepository.delete(activity);
    }


    /*
     * Helper:
     * make sure the trip exists.
     */
    private void checkTripExists(Long tripId) {
        if (!tripRepository.existsById(tripId)) {

            throw new ResponseStatusException( HttpStatus.NOT_FOUND, "Trip not found");
        }
    }


    /*
     * Helper:
     * retrieves an activity AND makes sure that
     * activity actually belongs to that trip.
     */
    private Activity findActivity( Long tripId, Long activityId) {

        return activityRepository
                .findByIdAndTripId( activityId, tripId)
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND, "Activity not found"));
    }


    /*
     * Simple business validation.
     */
    private void validateTimes( ZonedDateTime start, ZonedDateTime end) {

        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Activity end time cannot be before start time");
        }
    }
}