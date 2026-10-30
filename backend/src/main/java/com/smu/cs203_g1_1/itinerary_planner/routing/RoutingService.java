package com.smu.cs203_g1_1.itinerary_planner.routing;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import com.smu.cs203_g1_1.itinerary_planner.routing.dto.RouteResponse;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;

@Service
public class RoutingService {

    private static final ZoneId SINGAPORE_ZONE =
            ZoneId.of("Asia/Singapore");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ActivityRepository activityRepository;
    private final TripService tripService;
    private final OneMapClient oneMapClient;

    public RoutingService(
            ActivityRepository activityRepository,
            TripService tripService,
            OneMapClient oneMapClient) {

        this.activityRepository = activityRepository;
        this.tripService = tripService;
        this.oneMapClient = oneMapClient;
    }

    @Transactional(readOnly = true)
    public RouteResponse getRoute(
            String userId,
            String tripId,
            String fromActivityId,
            String toActivityId,
            RouteMode mode,
            OffsetDateTime requestedDeparture) {

        // Ensure the user belongs to the trip.
        tripService.getTripForMember(tripId, userId);

        if (fromActivityId.equals(toActivityId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot route between the same activity");
        }

        // Retrieve both activities within this trip.
        Activity fromActivity = findActivity(
                tripId, fromActivityId);

        Activity toActivity = findActivity(
                tripId, toActivityId);

        // Validate locations.
        List<Location> fromLocations =
                fromActivity.getLocations();

        List<Location> toLocations =
                toActivity.getLocations();

        if (fromLocations == null || fromLocations.isEmpty()
                || toLocations == null || toLocations.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both activities must have at least one location");
        }

        // Last location of the preceding activity.
        Location startLocation =
                fromLocations.get(fromLocations.size() - 1);

        // First location of the next activity.
        Location endLocation =
                toLocations.get(0);

        // Determine departure time.
        OffsetDateTime departure = requestedDeparture;

        if (departure == null
                && fromActivity.getEndDateTime() != null) {

            departure = fromActivity.getEndDateTime()
                    .toOffsetDateTime();
        }

        // Public transport requires a departure time.
        if (mode == RouteMode.PUBLIC_TRANSPORT
                && departure == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Departure date and time are required for public transport");
        }

        String date = null;
        String time = null;

        if (mode == RouteMode.PUBLIC_TRANSPORT) {

            OffsetDateTime singaporeDeparture =
                    departure.atZoneSameInstant(SINGAPORE_ZONE)
                            .toOffsetDateTime();

            date = singaporeDeparture.format(DATE_FORMAT);
            time = singaporeDeparture.format(TIME_FORMAT);
        }

        // Request route from OneMap.
        JsonNode routeData = oneMapClient.getRoute(
                startLocation.getLatitude(),
                startLocation.getLongitude(),
                endLocation.getLatitude(),
                endLocation.getLongitude(),
                mode,
                date,
                time);

        return new RouteResponse(
                fromActivityId,
                toActivityId,
                startLocation.getName(),
                endLocation.getName(),
                mode,
                departure,
                routeData);
    }

    private Activity findActivity(
            String tripId,
            String activityId) {

        return activityRepository
                .findByIdAndTripId(activityId, tripId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Activity not found in this trip"));
    }
}