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
import org.springframework.test.util.ReflectionTestUtils;
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

    static final long TRIP_ID = 10L;
    static final long ACTIVITY_ID = 100L;

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

        owner = user(1L, "clerk_owner");
        editor = user(2L, "clerk_editor");
        viewer = user(3L, "clerk_viewer");
        outsider = user(4L, "clerk_outsider");   // not a member of the trip
        newcomer = user(5L, "clerk_newcomer");   // a target to add as a member

        trip = new Trip("Test Trip", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), owner);
        ReflectionTestUtils.setField(trip, "id", TRIP_ID);
        trip.addMember(owner, TripRole.OWNER);
        trip.addMember(editor, TripRole.EDITOR);
        trip.addMember(viewer, TripRole.VIEWER);

        activity = new Activity();
        ReflectionTestUtils.setField(activity, "id", ACTIVITY_ID);
        trip.addActivity(activity);

        for (User u : List.of(owner, editor, viewer, outsider, newcomer)) {
            lenient().when(userRepository.findByClerkUserId(u.getClerkUserId())).thenReturn(Optional.of(u));
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
        Trip created = tripService.createTrip("New", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), "clerk_owner");

        assertTrue(created.isOwner(owner));
        assertEquals(TripRole.OWNER, created.findMember(owner).orElseThrow().getRole());
        assertEquals(1, created.getMembers().size());
    }

    @Test
    void addMember_defaultsToViewer() {
        Trip result = tripService.addMember(TRIP_ID, "clerk_owner", 5L, null);
        assertEquals(TripRole.VIEWER, result.findMember(newcomer).orElseThrow().getRole());
    }

    @Test
    void addMember_cannotAssignOwnerRole() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> tripService.addMember(TRIP_ID, "clerk_owner", 5L, TripRole.OWNER));
        assertTrue(trip.findMember(newcomer).isEmpty());
    }

    @Test
    void addMember_duplicateIsConflict() {
        assertStatus(HttpStatus.CONFLICT,
                () -> tripService.addMember(TRIP_ID, "clerk_owner", 2L, TripRole.VIEWER));
    }

    @Test
    void ownerRole_cannotBeChanged_orOwnerRemoved() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> tripService.changeMemberRole(TRIP_ID, "clerk_owner", 1L, TripRole.EDITOR));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> tripService.removeMember(TRIP_ID, "clerk_owner", 1L));
        assertEquals(TripRole.OWNER, trip.findMember(owner).orElseThrow().getRole());
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
        tripService.addMember(TRIP_ID, "clerk_owner", 5L, TripRole.EDITOR);
        assertEquals(TripRole.EDITOR, trip.findMember(newcomer).orElseThrow().getRole());

        tripService.changeMemberRole(TRIP_ID, "clerk_owner", 5L, TripRole.VIEWER);
        assertEquals(TripRole.VIEWER, trip.findMember(newcomer).orElseThrow().getRole());

        tripService.removeMember(TRIP_ID, "clerk_owner", 5L);
        assertTrue(trip.findMember(newcomer).isEmpty());
    }

    @Test
    void owner_canUpdateAndDeleteTrip() {
        Trip updated = tripService.updateTrip(TRIP_ID, "clerk_owner", "Renamed",
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 6));
        assertEquals("Renamed", updated.getName());

        tripService.deleteTrip(TRIP_ID, "clerk_owner");
        verify(tripRepository).delete(trip);
    }

    // ---------------------------------------------------------------
    // CGT-68: editor permissions
    // ---------------------------------------------------------------

    @Test
    void editor_cannotManageMembers() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.addMember(TRIP_ID, "clerk_editor", 5L, TripRole.VIEWER));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.changeMemberRole(TRIP_ID, "clerk_editor", 3L, TripRole.EDITOR));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(TRIP_ID, "clerk_editor", 3L));
        verify(tripRepository, never()).save(any());
    }

    @Test
    void editor_cannotUpdateOrDeleteTrip() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.updateTrip(TRIP_ID, "clerk_editor", "x", null, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.deleteTrip(TRIP_ID, "clerk_editor"));
        verify(tripRepository, never()).delete(any());
    }

    @Test
    void editor_canViewTrip() {
        assertEquals(TRIP_ID, tripService.getTrip(TRIP_ID, "clerk_editor").getId());
    }

    // ---------------------------------------------------------------
    // CGT-69: viewer permissions
    // ---------------------------------------------------------------

    @Test
    void viewer_canViewTrip() {
        assertEquals(TRIP_ID, tripService.getTrip(TRIP_ID, "clerk_viewer").getId());
    }

    @Test
    void viewer_cannotModifyAnything() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.updateTrip(TRIP_ID, "clerk_viewer", "x", null, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.deleteTrip(TRIP_ID, "clerk_viewer"));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.addMember(TRIP_ID, "clerk_viewer", 5L, TripRole.VIEWER));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(TRIP_ID, "clerk_viewer", 2L));
        verify(tripRepository, never()).save(any());
        verify(tripRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------
    // CGT-70: role check -> 403
    // ---------------------------------------------------------------

    @Test
    void nonMember_isForbiddenEverywhere() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.getTrip(TRIP_ID, "clerk_outsider"));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.updateTrip(TRIP_ID, "clerk_outsider", "x", null, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.addMember(TRIP_ID, "clerk_outsider", 5L, null));
    }

    @Test
    void missingTrip_is404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> tripService.getTrip(999L, "clerk_owner"));
    }

    @Test
    void getTripsForUser_onlyReturnsMemberTrips() {
        lenient().when(tripRepository.findByMembers_User_Id(1L)).thenReturn(List.of(trip));
        assertEquals(1, tripService.getTripsForUser("clerk_owner").size());
    }

    // ---------------------------------------------------------------
    // CGT-71: activity operations
    // ---------------------------------------------------------------

    @Test
    void viewer_canReadActivities() {
        assertEquals(1, activityService.getActivities(TRIP_ID, "clerk_viewer").size());
        assertEquals(ACTIVITY_ID, activityService.getActivity(TRIP_ID, ACTIVITY_ID, "clerk_viewer").getId());
    }

    @Test
    void nonMember_cannotReadActivities() {
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.getActivities(TRIP_ID, "clerk_outsider"));
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.getActivity(TRIP_ID, ACTIVITY_ID, "clerk_outsider"));
    }

    @Test
    void viewer_cannotCreateUpdateOrDeleteActivities() {
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.createActivity(TRIP_ID, new Activity(), "clerk_viewer"));
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.updateActivity(TRIP_ID, ACTIVITY_ID, new Activity(), "clerk_viewer"));
        assertStatus(HttpStatus.FORBIDDEN, () -> activityService.deleteActivity(TRIP_ID, ACTIVITY_ID, "clerk_viewer"));
        verify(activityRepository, never()).save(any());
        verify(activityRepository, never()).delete(any());
    }

    @Test
    void editor_canDeleteActivity() {
        activityService.deleteActivity(TRIP_ID, ACTIVITY_ID, "clerk_editor");
        verify(activityRepository).delete(activity);
        assertTrue(trip.getItinerary().isEmpty());
    }

    @Test
    void owner_canDeleteActivity() {
        activityService.deleteActivity(TRIP_ID, ACTIVITY_ID, "clerk_owner");
        verify(activityRepository).delete(activity);
    }

    @Test
    void editor_passesRoleCheckOnCreate() {
        // An empty activity gets past the role check and is rejected by validation
        // (400: no locations), which proves the editor was NOT stopped by a 403.
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> activityService.createActivity(TRIP_ID, new Activity(), "clerk_editor"));
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static void assertStatus(HttpStatus expected, Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected, ex.getStatusCode());
    }

    private static User user(long id, String clerkId) {
        User u = new User(clerkId, clerkId);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}