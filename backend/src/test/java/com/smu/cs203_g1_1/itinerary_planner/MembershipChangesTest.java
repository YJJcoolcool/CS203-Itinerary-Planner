package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.smu.cs203_g1_1.itinerary_planner.preference.PreferenceService;
import com.smu.cs203_g1_1.itinerary_planner.preference.PreferenceSummaryGenerator;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * CGT-168: the owner can hand the trip to another member.
 * CGT-167: any member except the owner can leave a trip.
 * CGT-173: preferences are only visible to people who share a trip.
 */
@DataJpaTest
@Import({TripService.class, PreferenceService.class})
class MembershipChangesTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private PreferenceService preferenceService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PreferenceSummaryGenerator summaryGenerator;

    private User owner;
    private User editor;
    private User viewer;
    private User outsider;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = saveUser("OwnerUs1", "clerk-owner");
        editor = saveUser("EditorU1", "clerk-editor");
        viewer = saveUser("ViewerU1", "clerk-viewer");
        outsider = saveUser("Outsidr1", "clerk-outsider");

        trip = tripService.createTrip("Singapore", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), owner.getId(), "sg");
        tripService.addMember(owner.getId(), trip.getId(), editor.getId(), TripRole.EDITOR);
        tripService.addMember(owner.getId(), trip.getId(), viewer.getId(), TripRole.VIEWER);
    }

    // ----- CGT-168: transfer ownership -----

    @Test
    void ownerCanHandTheTripToAMember() {
        tripService.transferOwnership(owner.getId(), trip.getId(), editor.getId());
        Trip reloaded = reload();

        assertEquals(editor.getId(), reloaded.getOwner().getId());
        assertEquals(TripRole.OWNER, reloaded.findRole(editor.getId()).orElseThrow());
        assertEquals(TripRole.EDITOR, reloaded.findRole(owner.getId()).orElseThrow());
    }

    @Test
    void theNewOwnerGetsOwnerPowersAndTheOldOneLosesThem() {
        tripService.transferOwnership(owner.getId(), trip.getId(), viewer.getId());
        flushAndClear();

        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.deleteTrip(owner.getId(), trip.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(owner.getId(), trip.getId(), editor.getId()));

        tripService.removeMember(viewer.getId(), trip.getId(), editor.getId());
        assertFalse(reload().findRole(editor.getId()).isPresent());
    }

    @Test
    void onlyTheOwnerCanTransfer() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.transferOwnership(editor.getId(), trip.getId(), viewer.getId()));
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.transferOwnership(outsider.getId(), trip.getId(), editor.getId()));
    }

    @Test
    void cannotTransferToANonMemberOrToYourself() {
        assertStatus(HttpStatus.NOT_FOUND, () -> tripService.transferOwnership(owner.getId(), trip.getId(), outsider.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> tripService.transferOwnership(owner.getId(), trip.getId(), owner.getId()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> tripService.transferOwnership(owner.getId(), trip.getId(), null));
    }

    // ----- CGT-167: leave a trip -----

    @Test
    void aMemberCanLeaveAndLosesAccess() {
        tripService.removeMember(viewer.getId(), trip.getId(), viewer.getId());
        flushAndClear();

        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.getTripForMember(trip.getId(), viewer.getId()));
        assertTrue(tripService.getTripsForUser(viewer.getId()).isEmpty());
    }

    @Test
    void theOwnerCannotLeaveWithoutTransferringFirst() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> tripService.removeMember(owner.getId(), trip.getId(), owner.getId()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("Transfer ownership"));
    }

    @Test
    void afterTransferringTheOldOwnerCanLeave() {
        tripService.transferOwnership(owner.getId(), trip.getId(), editor.getId());

        tripService.removeMember(owner.getId(), trip.getId(), owner.getId());

        assertFalse(reload().findRole(owner.getId()).isPresent());
    }

    @Test
    void outsidersCannotLeaveATripTheyAreNotOn() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(outsider.getId(), trip.getId(), outsider.getId()));
    }

    @Test
    void membersStillCannotRemoveOtherMembers() {
        assertStatus(HttpStatus.FORBIDDEN, () -> tripService.removeMember(editor.getId(), trip.getId(), viewer.getId()));
    }

    // ----- CGT-173: preference privacy -----

    @Test
    void youCanAlwaysReadYourOwnPreferences() {
        assertEquals(outsider.getId(), preferenceService.readUserFor(outsider.getId(), outsider.getId()).getUserId());
    }

    @Test
    void tripMatesCanReadEachOthersPreferences() {
        assertEquals(owner.getId(), preferenceService.readUserFor(viewer.getId(), owner.getId()).getUserId());
        assertEquals(viewer.getId(), preferenceService.readUserFor(editor.getId(), viewer.getId()).getUserId());
    }

    @Test
    void strangersCannotReadPreferences() {
        assertStatus(HttpStatus.FORBIDDEN, () -> preferenceService.readUserFor(outsider.getId(), owner.getId()));
    }

    @Test
    void leavingATripEndsAccessToItsMembersPreferences() {
        tripService.removeMember(viewer.getId(), trip.getId(), viewer.getId());
        flushAndClear();

        assertStatus(HttpStatus.FORBIDDEN, () -> preferenceService.readUserFor(viewer.getId(), owner.getId()));
    }

    // ----- helpers -----

    private Trip reload() {
        flushAndClear();
        return tripRepository.findById(trip.getId()).orElseThrow();
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
