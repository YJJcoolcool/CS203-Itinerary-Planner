package com.smu.cs203_g1_1.itinerary_planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityService;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationService;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

/**
 * Trip-role enforcement (CGT-67..72) using the real TripService / ActivityService
 * with mocked repositories. No Spring context or database needed.
 */
@ExtendWith(MockitoExtension.class)
class TripRoleTest {

    static final String TRIP_ID = "Trip0001";
    static final String ACTIVITY_ID = "Act00001";

    static final String OWNER = "Owner001";
    static final String EDITOR = "Editor01";
    static final String VIEWER = "Viewer01";
    static final String OUTSIDER = "Outsid01";   // not a member of the trip
    static final String NEWCOMER = "Newcom01";   // a target to add as a member

    @Mock TripRepository tripRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityRepository activityRepository;
    @Mock LocationService locationService;

    TripService tripService;
    ActivityService activityService;

    User owner, editor, viewer, outsider, newcomer;
    Trip trip;
    Activity activity;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, userRepository);
        activityService = new ActivityService(activityRepository, tripService, locationService);

        owner = user(OWNER);
        editor = user(EDITOR);
        viewer = user(VIEWER);
        outsider = user(OUTSIDER);
        newcomer = user(NEWCOMER);

        trip = new Trip("Test Trip", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), owner);
        trip.setId(TRIP_ID);
        trip.addMember(owner, TripRole.OWNER);
        trip.addMember(editor, TripRole.EDITOR);
        trip.addMember(viewer, TripRole.VIEWER);

        activity = new Activity();
        activity.setId(ACTIVITY_ID);
        trip.addActivity(activity);

        for (User u : List.of(owner, editor, viewer, outsider, newcomer)) {
            lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        lenient().when(tripRepository.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(activityRepository.findByTripIdOrderByStartDateTimeAsc(TRIP_ID))
                .thenReturn(List.of(activity));
        lenient().when(activityRepository.findByIdAndTripId(ACTIVITY_ID, TRIP_ID))
                .thenReturn(Optional.of(activity));
    }

    // ---------------------------------------------------------------
    // CGT-72: member roles, one owner
    // ---------------------------------------------------------------

    @Test
    void createTrip_creatorBecomesOwnerMember() {
        List<String> countries = List.of("sg");
        Trip created = tripService.createTrip("New", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), OWNER, countries);

        assertTrue(created.isOwner(owner));
        assertEquals(TripRole.OWNER, created.findRole(OWNER).orElseThrow());
        assertEquals(1, created.getUsers().size());
    }

    @Test
    void legacySingleArgAddMember_defaultsToViewer_andOwnerGetsOwner() {
        // existing callers (e.g. invitation acceptance) still use addMember(user)
        trip.addMember(newcomer);
        assertEquals(TripRole.VIEWER, trip.findRole(NEWCOMER).orElseThrow());

        trip.addMember(editor); // already a member: must NOT be downgraded
        assertEquals(TripRole.EDITOR, trip.findRole(EDITOR).orElseThrow());
    }

    @Test
    void memberWithNoStoredRole_isTreatedAsViewer() {
        // simulates rows that existed before roles were introduced
        Trip legacy = new Trip("Legacy", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), owner);
        legacy.getUsers().add(viewer);
        assertEquals(TripRole.VIEWER, legacy.findRole(VIEWER).orElseThrow());
        assertEquals(TripRole.OWNER, legacy.findRole(OWNER).orElseThrow());
    }

    @Test
    void addMember_defaultsToViewer() {
        Trip result = tripService.addMember(OWNER, TRIP_ID, NEWCOMER, null);
        assertEquals(TripRole.VIEWER, result.findRole(NEWCOMER).orElseThrow());
    }

    @Test
    void addMember_cannotAssignOwnerRole() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> tripService.addMember(OWNER, TRIP_ID, NEWCOMER, TripRole.OWNER));
        assertTrue(trip.findRole(NEWCOMER).isEmpty());
    }

    @Test
    void addMember_duplicateIsConflict() {
        assertStatus(HttpStatus.CONFLICT,
                () -> tripService.addMember(OWNER, TRIP_ID, EDITOR, TripRole.VIEWER));
    }

    @Test
    void ownerRole_cannotBeChanged_orOwnerRemoved() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> tripService.changeMemberRole(OWNER, TRIP_ID, OWNER, TripRole.EDITOR));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> tripService.removeMember(OWNER, TRIP_ID, OWNER));
        assertEquals(TripRole.OWNER, trip.findRole(OWNER).orElseThrow());
    }

    @Test
    void entity_rejectsSecondOwner() {
        assertThrows(IllegalArgumentException.class, () -> trip.addMember(newcomer, TripRole.OWNER));
        assertThrows(IllegalArgumentException.class, () -> trip.addMember(owner, TripRole.EDITOR));
    }

    @Test
    void tripRole_ordering() {
        assertTrue(TripRole.OWNER.atLeast(TripRole.EDITOR));
        assertTrue(TripRole.EDITOR.atLeast(TripRole.VIEWER));
        assertFalse(TripRole.VIEWER.atLeast(TripRole.EDITOR));
        assertFalse(TripRole.EDITOR.atLeast(TripRole.OWNER));
    }

    // ---------------------------------------------------------------
    // CGT-67: owner permissions
    // ---------------------------------------------------------------

    @Test
    void owner_canManageMembers() {
        tripService.addMember(OWNER, TRIP_ID, NEWCOMER, TripRole.EDITOR);
        assertEquals(TripRole.EDITOR, trip.findRole(NEWCOMER).orElseThrow());

        tripService.changeMemberRole(OWNER, TRIP_ID, NEWCOMER, TripRole.VIEWER);
        assertEquals(TripRole.VIEWER, trip.findRole(NEWCOMER).orElseThrow());

        tripService.removeMember(OWNER, TRIP_ID, NEWCOMER);
        assertTrue(trip.findRole(NEWCOMER).isEmpty());
    }

    @Test
    void owner_canUpdateAndDeleteTrip() {
        List<String> countries = List.of("my");
        Trip updated = tripService.updateTrip(OWNER, TRIP_ID, "Renamed",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 6), countries);
        assertEquals("Renamed", updated.getName());

        tripService.deleteTrip(OWNER, TRIP_ID);
        verify(tripRepository).delete(trip);
    }

    // ---------------------------------------------------------------
    // CGT-68: editor permissions
    // ---------------------------------------------------------------

    @Test
    void editor_cannotManageMembers() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.addMember(EDITOR, TRIP_ID, NEWCOMER, TripRole.VIEWER));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.changeMemberRole(EDITOR, TRIP_ID, VIEWER, TripRole.EDITOR));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(EDITOR, TRIP_ID, VIEWER));
        verify(tripRepository, never()).save(any());
    }

    @Test
    void editor_canUpdateTrip_butCannotDeleteTrip() {
        Trip updated = tripService.updateTrip(
                EDITOR,
                TRIP_ID,
                "Japan Trip",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 7),
                List.of("jp")
        );

        assertEquals("Japan Trip", updated.getName());
        assertEquals(LocalDate.of(2026, 11, 1), updated.getStartDate());
        assertEquals(LocalDate.of(2026, 11, 7), updated.getEndDate());
        assertEquals(List.of("jp"), updated.getCountries());

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> tripService.deleteTrip(EDITOR, TRIP_ID)
        );
    }

    @Test
    void editor_canViewTrip() {
        assertEquals(TRIP_ID, tripService.getTripForMember(TRIP_ID, EDITOR).getId());
    }

    // ---------------------------------------------------------------
    // CGT-69: viewer permissions
    // ---------------------------------------------------------------

    @Test
    void viewer_canViewTrip() {
        assertEquals(TRIP_ID, tripService.getTripForMember(TRIP_ID, VIEWER).getId());
    }

    @Test
    void viewer_cannotModifyAnything() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.updateTrip(VIEWER, TRIP_ID, "x", null, null, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.deleteTrip(VIEWER, TRIP_ID));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.addMember(VIEWER, TRIP_ID, NEWCOMER, TripRole.VIEWER));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(VIEWER, TRIP_ID, EDITOR));
        verify(tripRepository, never()).save(any());
        verify(tripRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------
    // CGT-70: role check -> 403
    // ---------------------------------------------------------------

    @Test
    void nonMember_isForbiddenEverywhere() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.getTripForMember(TRIP_ID, OUTSIDER));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.updateTrip(OUTSIDER, TRIP_ID, "x", null, null, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.addMember(OUTSIDER, TRIP_ID, NEWCOMER, null));
    }

    @Test
    void missingTrip_is404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> tripService.getTripForMember("NoSuchId", OWNER));
    }

    // ---------------------------------------------------------------
    // CGT-71: activity operations
    // ---------------------------------------------------------------

    @Test
    void viewer_canReadActivities() {
        assertEquals(1, activityService.getActivities(VIEWER, TRIP_ID).size());
        assertEquals(ACTIVITY_ID, activityService.getActivity(VIEWER, TRIP_ID, ACTIVITY_ID).getId());
    }

    @Test
    void nonMember_cannotReadActivities() {
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.getActivities(OUTSIDER, TRIP_ID));
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.getActivity(OUTSIDER, TRIP_ID, ACTIVITY_ID));
    }

    @Test
    void viewer_cannotCreateUpdateOrDeleteActivities() {
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.createActivity(VIEWER, TRIP_ID, new Activity()));
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.updateActivity(VIEWER, TRIP_ID, ACTIVITY_ID, new Activity()));
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.deleteActivity(VIEWER, TRIP_ID, ACTIVITY_ID));
        verify(activityRepository, never()).save(any());
        verify(activityRepository, never()).delete(any());
    }

    @Test
    void editor_canDeleteActivity() {
        activityService.deleteActivity(EDITOR, TRIP_ID, ACTIVITY_ID);
        verify(activityRepository).delete(activity);
        assertTrue(trip.getItinerary().isEmpty());
    }

    @Test
    void owner_canDeleteActivity() {
        activityService.deleteActivity(OWNER, TRIP_ID, ACTIVITY_ID);
        verify(activityRepository).delete(activity);
    }

    @Test
    void editor_passesRoleCheckOnCreate() {
        // An empty activity gets past the role check and is rejected by validation
        // (400: no locations), which proves the editor was NOT stopped by a 403.
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> activityService.createActivity(EDITOR, TRIP_ID, new Activity()));
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static void assertStatus(HttpStatus expected, Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected, ex.getStatusCode());
    }

    private static User user(String id) {
        User u = new User("clerk_" + id, id);
        u.setId(id);
        return u;
    }
}