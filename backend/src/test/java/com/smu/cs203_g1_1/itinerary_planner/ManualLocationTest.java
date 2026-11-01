package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationService;
import com.smu.cs203_g1_1.itinerary_planner.trips.ManualLocationRequest;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * CGT-142: manual places are private to the trip they were created in.
 * CGT-141: the user can mark a manual place as indoor or outdoor.
 */
@DataJpaTest
@Import({ActivityService.class, TripService.class, LocationService.class})
class ManualLocationTest {

    private static final ZonedDateTime START = ZonedDateTime.parse("2026-11-02T09:00:00+08:00");

    @Autowired
    private ActivityService activityService;

    @Autowired
    private TripService tripService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private GeoService geoService;

    private User owner;
    private User editor;
    private User viewer;
    private User outsider;
    private Trip trip;
    private Trip otherTrip;

    @BeforeEach
    void setUp() {
        owner = saveUser("OwnerUs1", "clerk-owner");
        editor = saveUser("EditorU1", "clerk-editor");
        viewer = saveUser("ViewerU1", "clerk-viewer");
        outsider = saveUser("Outsidr1", "clerk-outsider");

        trip = tripService.createTrip("Singapore", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), owner.getId(), "sg");
        tripService.addMember(owner.getId(), trip.getId(), editor.getId(), TripRole.EDITOR);
        tripService.addMember(owner.getId(), trip.getId(), viewer.getId(), TripRole.VIEWER);

        otherTrip = tripService.createTrip("Elsewhere", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), outsider.getId(), "sg");
    }

    // ----- CGT-142: creating -----

    @Test
    void editorCreatesAPlaceOwnedByTheTrip() {
        Location place = locationService.createManualLocation(trip.getId(), editor.getId(), request("Grandma's house", null));

        assertEquals(trip.getId(), place.getTrip().getId());
        assertEquals("sg", place.getCountryCode());
        assertTrue(place.isManual());
        assertFalse(place.isSeeded());
    }

    @Test
    void viewersAndOutsidersCannotCreate() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> locationService.createManualLocation(trip.getId(), viewer.getId(), request("Nope", null)));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> locationService.createManualLocation(trip.getId(), outsider.getId(), request("Nope", null)));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> locationService.createManualLocation(trip.getId(), owner.getId(), request("   ", null)));
    }

    // ----- CGT-142: seeing -----

    @Test
    void anyMemberCanSeeThePlaceButOutsidersCannot() {
        Location place = privatePlace();

        assertEquals(place.getId(), locationService.getVisibleLocation(place.getId(), viewer.getId()).getId());
        assertStatus(HttpStatus.FORBIDDEN, () -> locationService.getVisibleLocation(place.getId(), outsider.getId()));
    }

    @Test
    void sharedListNeverIncludesPrivatePlaces() {
        Location place = privatePlace();
        Location shared = sharedOsmPlace();

        List<String> ids = locationService.getSharedLocations().stream().map(Location::getId).toList();

        assertTrue(ids.contains(shared.getId()));
        assertFalse(ids.contains(place.getId()));
    }

    @Test
    void oldManualPlacesWithNoTripAreHidden() {
        Location unowned = locationService.createLocation(new Location("Old manual", null, 1.30, 103.80, null));

        assertThrows(LocationNotFoundException.class,
                () -> locationService.getVisibleLocation(unowned.getId(), owner.getId()));
    }

    // ----- CGT-142: using in activities -----

    @Test
    void theTripCanUseItsOwnPlace() {
        Location place = privatePlace();

        Activity created = activityService.createActivity(owner.getId(), trip.getId(), activityAt(place));

        assertEquals(place.getId(), created.getLocations().get(0).getId());
    }

    @Test
    void anotherTripCannotUseThePlace() {
        Location place = privatePlace();

        assertStatus(HttpStatus.FORBIDDEN,
                () -> activityService.createActivity(outsider.getId(), otherTrip.getId(), activityAt(place)));
    }

    @Test
    void anotherTripCannotSneakThePlaceInThroughAnEdit() {
        Location place = privatePlace();
        Activity theirs = activityService.createActivity(outsider.getId(), otherTrip.getId(), activityAt(sharedOsmPlace()));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> activityService.updateActivity(outsider.getId(), otherTrip.getId(), theirs.getId(), activityAt(place)));
    }

    // ----- CGT-142: deleting -----

    @Test
    void editorCanDeleteAnUnusedPlace() {
        Location place = privatePlace();

        locationService.deleteLocation(place.getId(), editor.getId());

        assertTrue(locationRepository.findById(place.getId()).isEmpty());
    }

    @Test
    void viewersAndOutsidersCannotDelete() {
        Location place = privatePlace();

        assertStatus(HttpStatus.FORBIDDEN, () -> locationService.deleteLocation(place.getId(), viewer.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> locationService.deleteLocation(place.getId(), outsider.getId()));
    }

    @Test
    void sharedPlacesCannotBeDeleted() {
        Location shared = sharedOsmPlace();

        assertStatus(HttpStatus.FORBIDDEN, () -> locationService.deleteLocation(shared.getId(), owner.getId()));
    }

    @Test
    void aPlaceStillInAnActivityCannotBeDeleted() {
        Location place = privatePlace();
        activityService.createActivity(owner.getId(), trip.getId(), activityAt(place));
        flushAndClear();

        assertStatus(HttpStatus.CONFLICT, () -> locationService.deleteLocation(place.getId(), owner.getId()));
    }

    @Test
    void deletingTheTripDeletesItsPrivatePlaces() {
        Location place = privatePlace();
        activityService.createActivity(owner.getId(), trip.getId(), activityAt(place));
        flushAndClear();

        tripService.deleteTrip(owner.getId(), trip.getId());
        flushAndClear();

        assertTrue(locationRepository.findById(place.getId()).isEmpty());
    }

    // ----- CGT-141: indoor / outdoor -----

    @Test
    void outdoorChoiceIsStoredAsAManualClassification() {
        Location place = locationService.createManualLocation(trip.getId(), owner.getId(), request("Picnic spot", "outdoor"));

        assertEquals("OUTDOOR", place.getIndoorClass());
        assertEquals("MANUAL", place.getIndoorSource());
        assertEquals(Boolean.FALSE, place.getIsIndoor());
    }

    @Test
    void indoorChoiceSetsTheLegacyFlagToo() {
        Location place = locationService.createManualLocation(trip.getId(), owner.getId(), request("Aunt's flat", "INDOOR"));

        assertEquals("INDOOR", place.getIndoorClass());
        assertEquals(Boolean.TRUE, place.getIsIndoor());
    }

    @Test
    void notSureLeavesThePlaceUnclassified() {
        Location place = locationService.createManualLocation(trip.getId(), owner.getId(), request("Somewhere", null));

        assertNull(place.getIndoorClass());
        assertNull(place.getIsIndoor());
    }

    @Test
    void unknownIndoorValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> locationService.createManualLocation(trip.getId(), owner.getId(), request("Somewhere", "SEMI")));
    }

    // ----- helpers -----

    private Location privatePlace() {
        return locationService.createManualLocation(trip.getId(), owner.getId(), request("Grandma's house", "OUTDOOR"));
    }

    private Location sharedOsmPlace() {
        Location shared = new Location("Gardens by the Bay", "park", 1.2816, 103.8636, false);
        shared.setId("SharedP1");
        shared.setOsmType("way");
        shared.setOsmId(123456L);
        return locationRepository.save(shared);
    }

    private static ManualLocationRequest request(String name, String indoorClass) {
        return new ManualLocationRequest(name, null, 1.3521, 103.8198, indoorClass);
    }

    private static Activity activityAt(Location place) {
        return new Activity("Visit", List.of(place), START, START.plusHours(2), "PLANNED");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertStatus(HttpStatus expected, Executable call) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected, error.getStatusCode());
    }

    private User saveUser(String id, String clerkUserId) {
        User user = new User(clerkUserId, clerkUserId);
        user.setId(id);
        return userRepository.save(user);
    }
}
