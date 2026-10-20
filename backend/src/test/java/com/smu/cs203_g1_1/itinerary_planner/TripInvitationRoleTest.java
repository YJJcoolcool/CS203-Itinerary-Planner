package com.smu.cs203_g1_1.itinerary_planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.InvitationStatus;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationResponse;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

/** Invitations carry the role the owner picked (Story 1, A1-A4). */
@DataJpaTest
@Import({TripInvitationService.class, TripService.class, UserService.class})
class TripInvitationRoleTest {

    @Autowired
    private TripInvitationService invitationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TripRepository tripRepository;

    private User owner;
    private User invitee;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = saveUser("clerk-owner", "Owner", "owner@test.com");
        invitee = saveUser("clerk-invitee", "Invitee", "invitee@test.com");

        trip = new Trip("Japan Autumn", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 10), owner);
        trip.setId(ID.generateID());
        trip.addMember(owner);
        trip = tripRepository.save(trip);
    }

    @Test
    void invitationWithoutARoleDefaultsToViewer() {
        TripInvitationResponse res = invitationService.sendInvitation(trip.getId(), owner.getId(), "invitee@test.com");

        assertEquals(TripRole.VIEWER, res.role());
    }

    @Test
    void responseShowsTheRoleTheOwnerChose() {
        TripInvitationResponse res = invitationService.sendInvitation(
                trip.getId(), owner.getId(), "invitee@test.com", TripRole.EDITOR);

        assertEquals(TripRole.EDITOR, res.role());
        assertEquals(TripRole.EDITOR, invitationService.getPendingForUser(invitee.getId()).get(0).role());
    }

    @Test
    void acceptingAnEditorInviteMakesTheUserAnEditor() {
        String id = invitationService.sendInvitation(
                trip.getId(), owner.getId(), "invitee@test.com", TripRole.EDITOR).id();

        invitationService.respond(id, invitee.getId(), InvitationStatus.ACCEPTED);

        assertEquals(TripRole.EDITOR, roleOf(invitee));
    }

    @Test
    void acceptingAViewerInviteMakesTheUserAViewer() {
        String id = invitationService.sendInvitation(
                trip.getId(), owner.getId(), "invitee@test.com", TripRole.VIEWER).id();

        invitationService.respond(id, invitee.getId(), InvitationStatus.ACCEPTED);

        assertEquals(TripRole.VIEWER, roleOf(invitee));
    }

    @Test
    void invitingAsOwnerIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> invitationService.sendInvitation(
                trip.getId(), owner.getId(), "invitee@test.com", TripRole.OWNER));
    }

    @Test
    void declinedInviteAddsNoRole() {
        String id = invitationService.sendInvitation(
                trip.getId(), owner.getId(), "invitee@test.com", TripRole.EDITOR).id();

        invitationService.respond(id, invitee.getId(), InvitationStatus.DECLINED);

        assertEquals(java.util.Optional.empty(), tripRepository.findById(trip.getId()).orElseThrow().findRole(invitee.getId()));
    }

    private TripRole roleOf(User user) {
        return tripRepository.findById(trip.getId()).orElseThrow().findRole(user.getId()).orElseThrow();
    }

    private User saveUser(String clerkId, String displayName, String email) {
        User user = new User(clerkId, displayName);
        user.setId(ID.generateID());
        user.setEmail(email);
        return userRepository.save(user);
    }

    private void assertStatus(HttpStatus expected, Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected.value(), e.getStatusCode().value());
    }
}