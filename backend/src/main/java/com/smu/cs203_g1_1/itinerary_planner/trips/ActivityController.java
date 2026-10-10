package com.smu.cs203_g1_1.itinerary_planner.trips;

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
@RequestMapping("/api/trips/{tripId}/activities")
public class ActivityController {

    private final ActivityService activityService;


    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }


    /*
     * GET /api/trips/5/activities
     *
     * Get all activities belonging to Trip 5. (VIEWER or above)
     */
    @GetMapping
    public List<Activity> getActivities(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId) {

        return activityService.getActivities(
                tripId,
                jwt.getSubject());
    }


    /*
     * GET /api/trips/5/activities/10
     *
     * Get Activity 10 belonging to Trip 5. (VIEWER or above)
     */
    @GetMapping("/{activityId}")
    public Activity getActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId) {

        return activityService.getActivity(
                tripId,
                activityId,
                jwt.getSubject());
    }


    /*
     * GET /api/trips/5/activities/10/locations
     *
     * Get all locations belonging to Activity 10 in Trip 5. (VIEWER or above)
     */
    @GetMapping("/{activityId}/locations")
    public List<Location> getActivityLocations(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId) {

        return activityService.getActivityLocations(
                tripId,
                activityId,
                jwt.getSubject());
    }


    /*
     * POST /api/trips/5/activities
     *
     * Create a NEW activity inside Trip 5. (EDITOR or above)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Activity createActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @RequestBody Activity activity) {

        return activityService.createActivity(
                tripId,
                activity,
                jwt.getSubject());
    }


    /*
     * PUT /api/trips/5/activities/10
     *
     * Update existing Activity 10. (EDITOR or above)
     */
    @PutMapping("/{activityId}")
    public Activity updateActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId,
            @RequestBody Activity activity) {

        return activityService.updateActivity(
                tripId,
                activityId,
                activity,
                jwt.getSubject());
    }


    /*
     * DELETE /api/trips/5/activities/10
     *
     * Delete Activity 10 from Trip 5. (EDITOR or above)
     */
    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("activityId") Long activityId) {

        activityService.deleteActivity(
                tripId,
                activityId,
                jwt.getSubject());
    }
}