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
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.InvitationStatus;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationResponse;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

import jakarta.persistence.EntityManager;

// Real services against in-memory H2 (no mocks): also exercises the derived queries,
// dirty checking and the Trip -> invitations cascade.
@DataJpaTest
@Import({TripInvitationService.class, TripService.class, UserService.class})
class TripInvitationServiceTest {

    @Autowired
    private TripInvitationService invitationService;

    @Autowired
    private TripService tripService;

    @Autowired
    private TripInvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User invitee;
    private User stranger;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = saveUser("clerk-owner", "Owner", "owner@test.com");
        invitee = saveUser("clerk-invitee", "Invitee", "invitee@test.com");
        stranger = saveUser("clerk-stranger", "Stranger", "stranger@test.com");

        trip = new Trip("Japan Autumn", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 10), owner);
        trip.setId(ID.generateID());
        trip.addMember(owner);
        trip = tripRepository.save(trip);
    }

    // ----- sending -----

    @Test
    void sendInvitation_createsPendingInviteWith8CharId() {
        // Email lookup ignores case and surrounding whitespace.
        TripInvitationResponse res = invitationService.sendInvitation(trip.getId(), owner.getId(), " INVITEE@test.com ");

        assertTrue(res.id().matches("[0-9A-Za-z]{8}"));
        assertEquals(InvitationStatus.PENDING, res.status());
        assertEquals("Owner", res.senderDisplayName());
        assertEquals("Invitee", res.receiverDisplayName());
        assertEquals("Japan Autumn", res.tripName());
    }

    @Test
    void getPendingForUser_onlyReturnsReceiversInvites() {
        invite();

        assertEquals(1, invitationService.getPendingForUser(invitee.getId()).size());
        assertTrue(invitationService.getPendingForUser(stranger.getId()).isEmpty());
    }

    @Test
    void nonOwner_cannotSendListOrCancel() {
        String id = invite().id();

        assertStatus(HttpStatus.FORBIDDEN,
                () -> invitationService.sendInvitation(trip.getId(), stranger.getId(), "invitee@test.com"));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> invitationService.getPendingForTrip(trip.getId(), stranger.getId()));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> invitationService.cancel(trip.getId(), id, stranger.getId()));
    }

    @Test
    void sendInvitation_toSelf_is400() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> invitationService.sendInvitation(trip.getId(), owner.getId(), "owner@test.com"));
    }

    @Test
    void sendInvitation_toExistingMember_is409() {
        trip.addMember(invitee);

        assertStatus(HttpStatus.CONFLICT,
                () -> invitationService.sendInvitation(trip.getId(), owner.getId(), "invitee@test.com"));
    }

    @Test
    void sendInvitation_duplicatePending_is409() {
        invite();

        assertStatus(HttpStatus.CONFLICT,
                () -> invitationService.sendInvitation(trip.getId(), owner.getId(), "invitee@test.com"));
    }

    @Test
    void sendInvitation_unknownEmail_throwsUserNotFound() {
        assertThrows(UserNotFoundException.class,
                () -> invitationService.sendInvitation(trip.getId(), owner.getId(), "nobody@test.com"));
    }

    // ----- responding -----

    @Test
    void accept_addsMemberAndClearsInbox() {
        String id = invite().id();

        TripInvitationResponse res = invitationService.respond(id, invitee.getId(), InvitationStatus.ACCEPTED);

        assertEquals(InvitationStatus.ACCEPTED, res.status());
        assertTrue(isMember(invitee));
        assertTrue(invitationService.getPendingForUser(invitee.getId()).isEmpty());
    }

    @Test
    void decline_doesNotAddMember() {
        String id = invite().id();

        TripInvitationResponse res = invitationService.respond(id, invitee.getId(), InvitationStatus.DECLINED);

        assertEquals(InvitationStatus.DECLINED, res.status());
        assertFalse(isMember(invitee));
    }

    @Test
    void respondTwice_is409() {
        String id = invite().id();
        invitationService.respond(id, invitee.getId(), InvitationStatus.ACCEPTED);

        assertStatus(HttpStatus.CONFLICT,
                () -> invitationService.respond(id, invitee.getId(), InvitationStatus.DECLINED));
    }

    @Test
    void respond_byNonReceiver_is403() {
        String id = invite().id();

        assertStatus(HttpStatus.FORBIDDEN,
                () -> invitationService.respond(id, stranger.getId(), InvitationStatus.ACCEPTED));
    }

    @Test
    void respond_withNonDecisionStatus_is400() {
        String id = invite().id();

        assertStatus(HttpStatus.BAD_REQUEST,
                () -> invitationService.respond(id, invitee.getId(), InvitationStatus.PENDING));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> invitationService.respond(id, invitee.getId(), InvitationStatus.CANCELLED));
    }

    // ----- cancelling -----

    @Test
    void cancel_marksCancelledAndBlocksAccept() {
        String id = invite().id();

        invitationService.cancel(trip.getId(), id, owner.getId());

        assertEquals(InvitationStatus.CANCELLED, invitationRepository.findById(id).orElseThrow().getStatus());
        assertTrue(invitationService.getPendingForUser(invitee.getId()).isEmpty());
        assertStatus(HttpStatus.CONFLICT,
                () -> invitationService.respond(id, invitee.getId(), InvitationStatus.ACCEPTED));
    }

    @Test
    void cancel_viaWrongTripId_is404() {
        String id = invite().id();
        Trip otherTrip = new Trip("Other", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), owner);
        otherTrip.setId(ID.generateID());
        String otherTripId = tripRepository.save(otherTrip).getId();

        assertStatus(HttpStatus.NOT_FOUND,
                () -> invitationService.cancel(otherTripId, id, owner.getId()));
    }

    // ----- cascade -----

    @Test
    void deletingTrip_deletesItsInvitations() {
        invite();
        // Flush + clear so the trip is reloaded from the DB like in a real request.
        entityManager.flush();
        entityManager.clear();

        tripService.deleteTrip(owner.getId(), trip.getId());
        // Flush forces the DELETEs to hit H2, so an FK violation would surface here.
        entityManager.flush();

        assertEquals(0, invitationRepository.count());
    }

    // ----- helpers -----

    private TripInvitationResponse invite() {
        return invitationService.sendInvitation(trip.getId(), owner.getId(), "invitee@test.com");
    }

    private User saveUser(String clerkId, String displayName, String email) {
        User user = new User(clerkId, displayName);
        user.setId(ID.generateID());
        user.setEmail(email);
        return userRepository.save(user);
    }

    private boolean isMember(User user) {
        return tripRepository.findById(trip.getId()).orElseThrow()
                .getUsers().stream().anyMatch(u -> u.getId().equals(user.getId()));
    }

    private void assertStatus(HttpStatus expected, Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected.value(), e.getStatusCode().value());
    }
}
