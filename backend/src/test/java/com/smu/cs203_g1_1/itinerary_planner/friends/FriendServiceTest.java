package com.smu.cs203_g1_1.itinerary_planner.friends;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.friends.FriendService.FriendResponse;
import com.smu.cs203_g1_1.itinerary_planner.friends.FriendService.Relation;
import com.smu.cs203_g1_1.itinerary_planner.friends.FriendService.RelationResponse;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({FriendService.class, TripService.class, UserService.class})
class FriendServiceTest {

    @Autowired
    private FriendService friendService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private EntityManager entityManager;

    private User alice;
    private User bob;
    private User stranger;

    @BeforeEach
    void setUp() {
        alice = saveUser("clerk-alice", "Alice", "alice@test.com");
        bob = saveUser("clerk-bob", "Bob", "bob@test.com");
        stranger = saveUser("clerk-stranger", "Stranger", "stranger@test.com");

        Trip trip = new Trip("Japan Autumn", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 10), alice);
        trip.setId(ID.generateID());
        trip.addMember(alice);
        trip.addMember(bob);
        tripRepository.save(trip);
    }

    @Test
    void sendRequest_isOutgoingForSenderAndIncomingForReceiver() {
        RelationResponse sent = friendService.sendRequest(alice.getId(), bob.getId());

        assertEquals(Relation.OUTGOING, sent.relation());
        assertEquals(Relation.INCOMING, friendService.relation(bob.getId(), alice.getId()).relation());
        assertEquals(sent.requestId(), friendService.incomingRequests(bob.getId()).get(0).id());
        assertEquals("Alice", friendService.incomingRequests(bob.getId()).get(0).displayName());
    }

    @Test
    void relation_withNoRow_isNone() {
        RelationResponse relation = friendService.relation(alice.getId(), bob.getId());

        assertEquals(Relation.NONE, relation.relation());
        assertNull(relation.requestId());
    }

    @Test
    void sendRequest_withoutSharedTrip_is403() {
        assertStatus(HttpStatus.FORBIDDEN, () -> friendService.sendRequest(alice.getId(), stranger.getId()));
    }

    @Test
    void sendRequest_toSelf_is400() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> friendService.sendRequest(alice.getId(), alice.getId()));
    }

    @Test
    void sendRequest_twice_is409() {
        friendService.sendRequest(alice.getId(), bob.getId());

        assertStatus(HttpStatus.CONFLICT, () -> friendService.sendRequest(alice.getId(), bob.getId()));
    }

    @Test
    void sendRequest_backFromTheOtherSide_acceptsTheExistingRequest() {
        friendService.sendRequest(alice.getId(), bob.getId());

        assertEquals(Relation.FRIENDS, friendService.sendRequest(bob.getId(), alice.getId()).relation());
        assertEquals(1, friendshipRepository.count());
    }

    @Test
    void accept_makesBothFriendsAndSharesEmail() {
        String requestId = friendService.sendRequest(alice.getId(), bob.getId()).requestId();

        friendService.respond(bob.getId(), requestId, true);

        assertEquals(List.of(new FriendResponse(bob.getId(), "Bob", "bob@test.com")),
                friendService.friends(alice.getId()));
        assertEquals(List.of(new FriendResponse(alice.getId(), "Alice", "alice@test.com")),
                friendService.friends(bob.getId()));
        assertTrue(friendService.incomingRequests(bob.getId()).isEmpty());
        assertStatus(HttpStatus.CONFLICT, () -> friendService.respond(bob.getId(), requestId, true));
    }

    @Test
    void reject_deletesTheRequestSoItCanBeSentAgain() {
        String requestId = friendService.sendRequest(alice.getId(), bob.getId()).requestId();

        friendService.respond(bob.getId(), requestId, false);

        assertEquals(Relation.NONE, friendService.relation(alice.getId(), bob.getId()).relation());
        assertEquals(Relation.OUTGOING, friendService.sendRequest(alice.getId(), bob.getId()).relation());
    }

    @Test
    void respond_bySomeoneOtherThanTheAddressee_is404() {
        String requestId = friendService.sendRequest(alice.getId(), bob.getId()).requestId();

        assertStatus(HttpStatus.NOT_FOUND, () -> friendService.respond(alice.getId(), requestId, true));
    }

    @Test
    void remove_unfriendsForBothSides() {
        String requestId = friendService.sendRequest(alice.getId(), bob.getId()).requestId();
        friendService.respond(bob.getId(), requestId, true);

        // Either side can remove; the addressee does it here.
        friendService.remove(bob.getId(), alice.getId());

        assertTrue(friendService.friends(alice.getId()).isEmpty());
        assertTrue(friendService.friends(bob.getId()).isEmpty());
        assertEquals(Relation.NONE, friendService.relation(alice.getId(), bob.getId()).relation());
    }

    @Test
    void remove_cancelsAnOutgoingRequestSoItCanBeSentAgain() {
        friendService.sendRequest(alice.getId(), bob.getId());

        friendService.remove(alice.getId(), bob.getId());

        assertTrue(friendService.incomingRequests(bob.getId()).isEmpty());
        assertEquals(Relation.OUTGOING, friendService.sendRequest(alice.getId(), bob.getId()).relation());
    }

    @Test
    void remove_anIncomingRequest_is404() {
        friendService.sendRequest(alice.getId(), bob.getId());

        assertStatus(HttpStatus.NOT_FOUND, () -> friendService.remove(bob.getId(), alice.getId()));
        assertEquals(Relation.INCOMING, friendService.relation(bob.getId(), alice.getId()).relation());
    }

    @Test
    void remove_withNoRelation_is404() {
        assertStatus(HttpStatus.NOT_FOUND, () -> friendService.remove(alice.getId(), bob.getId()));
    }

    @Test
    void deletingAUser_removesTheirFriendships() {
        // Saved directly: stranger is on no trip, so the user row can be deleted on its own.
        friendshipRepository.save(new Friendship(ID.generateID(), alice, stranger));
        entityManager.flush();
        entityManager.clear();

        userRepository.deleteById(stranger.getId());
        entityManager.flush();

        assertEquals(0, friendshipRepository.count());
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
