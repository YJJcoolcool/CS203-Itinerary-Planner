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

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
@RequestMapping("/api/trips/{tripId}/activities")
public class ActivityController {

    private final ActivityService activityService;
    private final UserService userService;


    public ActivityController(ActivityService activityService, UserService userService) {
        this.activityService = activityService;
        this.userService = userService;
    }


    /*
     * GET /api/trips/5/activities
     *
     * Get all activities belonging to Trip 5.
     */
    @GetMapping
    public List<Activity> getActivities(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId) {

        return activityService.getActivities(
                userService.getCurrentUserId(jwt),
                tripId);
    }


    /*
     * GET /api/trips/5/activities/10
     *
     * Get Activity 10 belonging to Trip 5.
     */
    @GetMapping("/{activityId}")
    public Activity getActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @PathVariable("activityId") String activityId) {

        return activityService.getActivity(
                userService.getCurrentUserId(jwt),
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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @PathVariable("activityId") String activityId) {

        return activityService.getActivityLocations(
                userService.getCurrentUserId(jwt),
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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @RequestBody Activity activity) {

        return activityService.createActivity(
                userService.getCurrentUserId(jwt),
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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @PathVariable("activityId") String activityId,
            @RequestBody Activity activity) {

        return activityService.updateActivity(
                userService.getCurrentUserId(jwt),
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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @PathVariable("activityId") String activityId) {

        activityService.deleteActivity(
                userService.getCurrentUserId(jwt),
                tripId,
                activityId);
    }
}