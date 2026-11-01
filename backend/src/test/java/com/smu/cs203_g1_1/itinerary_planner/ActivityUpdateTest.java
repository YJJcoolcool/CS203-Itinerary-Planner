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
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationService;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

import jakarta.persistence.EntityManager;

// editing an activity's name, times and locations through PUT.
@DataJpaTest
@Import({ActivityService.class, TripService.class, LocationService.class})
class ActivityUpdateTest {

    private static final ZonedDateTime START = ZonedDateTime.parse("2026-11-02T09:00:00+09:00");

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

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private Trip trip;
    private Location sensoji;
    private Location skytree;
    private Location ueno;
    private Activity activity;

    @BeforeEach
    void setUp() {
        owner = saveUser("OwnerUs1", "clerk-owner");

        trip = tripService.createTrip("Japan", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), owner.getId(), "jp");
        sensoji = locationService.createLocation(new Location("Senso-ji", "Temple", 35.7148, 139.7967, false));
        skytree = locationService.createLocation(new Location("Skytree", "Tower", 35.7101, 139.8107, true));
        ueno = locationService.createLocation(new Location("Ueno Park", "Park", 35.7156, 139.7745, false));
        activity = activityService.createActivity(owner.getId(), trip.getId(),
                new Activity("Visit Senso-ji", List.of(sensoji), START, START.plusHours(2), "PLANNED"));
    }

    @Test
    void update_changesNameTimesAndLocationsInOrder() {
        Activity changes = new Activity("Asakusa day", List.of(skytree, ueno), START.plusHours(1), START.plusHours(5), "PLANNED");

        Activity updated = activityService.updateActivity(owner.getId(), trip.getId(), activity.getId(), changes);

        assertEquals("Asakusa day", updated.getName());
        assertEquals(START.plusHours(1).toInstant(), updated.getStartDateTime().toInstant());
        assertEquals(START.plusHours(5).toInstant(), updated.getEndDateTime().toInstant());
        // Senso-ji was replaced, and the new locations keep the order they were sent in.
        List<String> names = activityService.getActivityLocations(owner.getId(), trip.getId(), activity.getId())
                .stream().map(Location::getName).toList();
        assertEquals(List.of("Skytree", "Ueno Park"), names);
    }

    @Test
    void update_withoutStatus_keepsCurrentStatus() {
        Activity changes = new Activity("Renamed", List.of(sensoji), START, START.plusHours(2), null);

        Activity updated = activityService.updateActivity(owner.getId(), trip.getId(), activity.getId(), changes);

        assertEquals("PLANNED", updated.getStatus());
    }

    @Test
    void update_withNoLocations_is400() {
        Activity changes = new Activity("No places", List.of(), START, START.plusHours(2), null);

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> activityService.updateActivity(owner.getId(), trip.getId(), activity.getId(), changes));
    }

    @Test
    void update_withUnknownLocation_isNotFound() {
        Location missing = new Location("Ghost", "None", 0.0, 0.0, false);
        missing.setId("NoSuchId");
        Activity changes = new Activity("Ghost trip", List.of(missing), START, START.plusHours(2), null);

        assertThrows(LocationNotFoundException.class,
                () -> activityService.updateActivity(owner.getId(), trip.getId(), activity.getId(), changes));
    }

    @Test
    void update_endBeforeStart_is400() {
        Activity changes = new Activity("Backwards", List.of(sensoji), START, START.minusHours(1), null);

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> activityService.updateActivity(owner.getId(), trip.getId(), activity.getId(), changes));
    }

    @Test
    void update_activityFromAnotherTrip_is404() {
        Trip otherTrip = tripService.createTrip("Korea", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), owner.getId(), "kr");
        Activity changes = new Activity("Wrong trip", List.of(sensoji), START, START.plusHours(2), null);

        assertStatus(HttpStatus.NOT_FOUND,
                () -> activityService.updateActivity(owner.getId(), otherTrip.getId(), activity.getId(), changes));
    }

    // ----- CGT-151: the order of locations is the order of visits -----

    @Test
    void create_savesLocationsInTheOrderSent() {
        Activity created = activityService.createActivity(owner.getId(), trip.getId(),
                new Activity("Asakusa loop", List.of(ueno, sensoji, skytree), START, START.plusHours(4), "PLANNED"));
        flushAndClear();

        assertEquals(List.of("Ueno Park", "Senso-ji", "Skytree"), savedLocationNames(created.getId()));
    }

    @Test
    void update_reorderingTheSameLocationsIsSaved() {
        Activity created = activityService.createActivity(owner.getId(), trip.getId(),
                new Activity("Asakusa loop", List.of(sensoji, skytree, ueno), START, START.plusHours(4), "PLANNED"));
        flushAndClear();

        Activity reordered = new Activity("Asakusa loop", List.of(ueno, sensoji, skytree), START, START.plusHours(4), null);
        activityService.updateActivity(owner.getId(), trip.getId(), created.getId(), reordered);
        flushAndClear();

        assertEquals(List.of("Ueno Park", "Senso-ji", "Skytree"), savedLocationNames(created.getId()));
    }

    /** Reads the order back from the database, not from Hibernate's in-memory copy. */
    private List<String> savedLocationNames(String activityId) {
        return activityService.getActivityLocations(owner.getId(), trip.getId(), activityId)
                .stream().map(Location::getName).toList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable call) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected, error.getStatusCode());
    }

    private User saveUser(String id, String clerkUserId) {
        User user = new User(clerkUserId, clerkUserId);
        user.setId(id);
        return userRepository.save(user);
    }
}