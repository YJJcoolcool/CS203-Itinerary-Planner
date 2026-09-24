package com.smu.cs203_g1_1.itinerary_planner.trips;

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
@RequestMapping("/api/trips/{tripId}/activities")
public class ActivityController {

    private final ActivityService activityService;


    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }


    /*
     * GET /api/trips/5/activities
     *
     * Get all activities belonging to Trip 5.
     */
    @GetMapping
    public List<Activity> getActivities(
            @PathVariable("tripId") Long tripId) {

        return activityService.getActivities(tripId);
    }


    /*
     * GET /api/trips/5/activities/10
     *
     * Get Activity 10 belonging to Trip 5.
     */
    @GetMapping("/{activityId}")
    public Activity getActivity(
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId) {

        return activityService.getActivity(
                tripId,
                activityId);
    }


    /*
     * GET /api/trips/5/activities/10/locations
     *
     * Get all locations belonging to Activity 10 in Trip 5.
     */
    @GetMapping("/{activityId}/locations")
    public List<Location> getActivityLocations(
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId) {

        return activityService.getActivityLocations(
                tripId,
                activityId);
    }


    /*
     * POST /api/trips/5/activities
     *
     * Create a NEW activity inside Trip 5.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Activity createActivity(
            @PathVariable("tripId") Long tripId,
            @RequestBody Activity activity) {

        return activityService.createActivity(
                tripId,
                activity);
    }


    /*
     * PUT /api/trips/5/activities/10
     *
     * Update existing Activity 10.
     */
    @PutMapping("/{activityId}")
    public Activity updateActivity(
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId,
            @RequestBody Activity activity) {

        return activityService.updateActivity(
                tripId,
                activityId,
                activity);
    }


    /*
     * DELETE /api/trips/5/activities/10
     *
     * Delete Activity 10 from Trip 5.
     */
    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId) {

        activityService.deleteActivity(
                tripId,
                activityId);
    }
}
