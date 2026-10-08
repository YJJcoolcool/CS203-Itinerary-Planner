package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.util.ID;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository activityRepository;
    private final TripService tripService;
    private final LocationService locationService;

    public ActivityService(
            ActivityRepository activityRepository,
            TripService tripService,
            LocationService locationService) {

        this.activityRepository = activityRepository;
        this.tripService = tripService;
        this.locationService = locationService;
    }


    /*
     * GET all activities belonging to one trip.
     */
    @Transactional(readOnly = true)
    public List<Activity> getActivities(String tripId) {

        // Also checks that the trip exists.
        tripService.getTrip(tripId);

        return activityRepository
                .findByTripIdOrderByStartDateTimeAsc(tripId);
    }


    /*
     * GET one activity belonging to one specific trip.
     */
    @Transactional(readOnly = true)
    public Activity getActivity(
            String tripId,
            String activityId) {

        return activityRepository
                .findByIdAndTripId(activityId, tripId)
                .orElseThrow(()
                        -> {
                    log.warn("Activity not found: id={} tripId={}", activityId, tripId);
                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Activity not found");
                });
    }


    /*
     * GET all locations belonging to one activity in one specific trip.
     */
    @Transactional(readOnly = true)
    public List<Location> getActivityLocations(
            String tripId,
            String activityId) {

        Activity activity = getActivity(tripId, activityId);

        return new ArrayList<>(activity.getLocations());
    }


    /*
     * CREATE a new activity inside a trip.
     */
    @Transactional
    public Activity createActivity(
            String tripId,
            Activity activity) {

        Trip trip = tripService.getTrip(tripId);

        validateTimes(
                activity.getStartDateTime(),
                activity.getEndDateTime());

        List<Location> locations
                = resolveLocations(activity.getLocations());

        activity.setLocations(locations);

        String id = ID.generateUniqueID(activityRepository::existsById);
        activity.setId(id);

        // Maintains both:
        // Trip.itinerary
        // Activity.trip
        trip.addActivity(activity);

        Activity saved = activityRepository.save(activity);
        log.info("Created activity id={} in trip id={}", saved.getId(), tripId);
        return saved;
    }


    /*
     * UPDATE an existing activity.
     */
    @Transactional
    public Activity updateActivity(
            String tripId,
            String activityId,
            Activity updatedActivity) {

        Activity activity
                = getActivity(tripId, activityId);

        validateTimes(
                updatedActivity.getStartDateTime(),
                updatedActivity.getEndDateTime());

        List<Location> locations
                = resolveLocations(
                        updatedActivity.getLocations());

        activity.setName(
                updatedActivity.getName());

        activity.setStartDateTime(
                updatedActivity.getStartDateTime());

        activity.setEndDateTime(
                updatedActivity.getEndDateTime());

        activity.setStatus(
                updatedActivity.getStatus());

        activity.setLocations(locations);

        Activity saved = activityRepository.save(activity);
        log.info("Updated activity id={} in trip id={}", activityId, tripId);
        return saved;
    }


    /*
     * DELETE an activity.
     */
    @Transactional
    public void deleteActivity(
            String tripId,
            String activityId) {

        Activity activity
                = getActivity(tripId, activityId);

        /*
         * Keep the in-memory Trip itinerary consistent too.
         */
        activity.getTrip().removeActivity(activity);

        activityRepository.delete(activity);

        log.info("Deleted activity id={} from trip id={}", activityId, tripId);
    }


    /*
     * Takes the Location objects supplied in the incoming Activity,
     * extracts their IDs, and retrieves the actual persisted
     * Location objects.
     */
    private List<Location> resolveLocations(
            List<Location> requestedLocations) {

        if (requestedLocations == null
                || requestedLocations.isEmpty()) {

            log.warn("Activity rejected: no locations provided");
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Activity must contain at least one location");
        }

        List<Location> resolvedLocations
                = new ArrayList<>();

        for (Location location : requestedLocations) {

            if (location == null
                    || location.getId() == null) {

                log.warn("Activity rejected: location without a valid id");
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Every location must contain a location id");
            }

            Location resolvedLocation = locationService.getLocationById(location.getId());

            resolvedLocations.add(resolvedLocation);
        }

        return resolvedLocations;
    }


    /*
     * Activity cannot end before it starts.
     */
    private void validateTimes(
            ZonedDateTime startDateTime,
            ZonedDateTime endDateTime) {

        if (startDateTime != null
                && endDateTime != null
                && endDateTime.isBefore(startDateTime)) {

            log.warn("Activity rejected: end {} is before start {}", endDateTime, startDateTime);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Activity end time cannot be before start time");
        }
    }
}
