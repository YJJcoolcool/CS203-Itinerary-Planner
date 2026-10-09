package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.geo.GeoService;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityService;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationService;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@DataJpaTest
@Import({ActivityService.class, TripService.class, LocationService.class})
class ActivityMembershipTest {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private TripService tripService;

    @Autowired
    private LocationService locationService;

    @MockitoBean
    private GeoService geoService;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User outsider;
    private Trip trip;
    private Location location;
    private Activity activity;

    @BeforeEach
    void setUp() {
        owner = saveUser("OwnerUs1", "clerk-owner");
        outsider = saveUser("Outsidr1", "clerk-outsider");

        trip = tripService.createTrip("Japan", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), owner.getId());
        location = locationService.createLocation(new Location("Senso-ji", "Temple", 35.7148, 139.7967, false));
        activity = activityService.createActivity(owner.getId(), trip.getId(), newActivity("Visit Senso-ji"));
    }

    @Test
    void memberCanListActivities() {
        List<Activity> activities = activityService.getActivities(owner.getId(), trip.getId());
        assertEquals(1, activities.size());
    }

    @Test
    void nonMemberCannotListActivities() {
        assertForbidden(() -> activityService.getActivities(outsider.getId(), trip.getId()));
    }

    @Test
    void nonMemberCannotViewActivityLocations() {
        assertForbidden(() -> activityService.getActivityLocations(outsider.getId(), trip.getId(), activity.getId()));
    }

    @Test
    void nonMemberCannotCreateActivity() {
        assertForbidden(() -> activityService.createActivity(outsider.getId(), trip.getId(), newActivity("Sneaky")));
    }

    @Test
    void nonMemberCannotDeleteActivity() {
        assertForbidden(() -> activityService.deleteActivity(outsider.getId(), trip.getId(), activity.getId()));
        // Still there for the real member.
        assertEquals(1, activityService.getActivities(owner.getId(), trip.getId()).size());
    }

    @Test
    void missingTripIsNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> activityService.getActivities(owner.getId(), "NoSuchId"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    private Activity newActivity(String name) {
        ZonedDateTime start = ZonedDateTime.parse("2026-11-02T09:00:00+09:00");
        return new Activity(name, List.of(location), start, start.plusHours(2), "PLANNED");
    }

    private void assertForbidden(org.junit.jupiter.api.function.Executable call) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    private User saveUser(String id, String clerkUserId) {
        User user = new User(clerkUserId, clerkUserId);
        user.setId(id);
        return userRepository.save(user);
    }
}
