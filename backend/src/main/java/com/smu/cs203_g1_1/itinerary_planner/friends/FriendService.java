package com.smu.cs203_g1_1.itinerary_planner.friends;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

/** Friend requests and friend lists (CGT-163). */
@Service
public class FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendService.class);

    /** How the viewer relates to another user, from the viewer's side. */
    public enum Relation { NONE, OUTGOING, INCOMING, FRIENDS }

    public record RelationResponse(Relation relation, String requestId) { }

    /** Friends see each other's email so it can prefill trip invites (CGT-182). */
    public record FriendResponse(String userId, String displayName, String email) { }

    public record FriendRequestResponse(String id, String userId, String displayName, Instant createdAt) { }

    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final TripService tripService;

    public FriendService(FriendshipRepository friendshipRepository, UserService userService, TripService tripService) {
        this.friendshipRepository = friendshipRepository;
        this.userService = userService;
        this.tripService = tripService;
    }

    @Transactional(readOnly = true)
    public RelationResponse relation(String viewerId, String otherId) {
        return friendshipRepository.findBetween(viewerId, otherId)
                .map(f -> toRelation(f, viewerId))
                .orElse(new RelationResponse(Relation.NONE, null));
    }

    /** Sends a request, or accepts theirs if the other user already asked. */
    @Transactional
    public RelationResponse sendRequest(String requesterId, String targetId) {
        if (requesterId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot add yourself");
        }
        User target = userService.getUserById(targetId);
        if (!tripService.sharesTrip(requesterId, targetId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only add people you share a trip with");
        }

        Friendship existing = friendshipRepository.findBetween(requesterId, targetId).orElse(null);
        if (existing == null) {
            Friendship created = friendshipRepository.save(new Friendship(
                    ID.generateUniqueID(friendshipRepository::existsById),
                    userService.getUserById(requesterId),
                    target));
            log.info("User id={} sent friend request {} to user id={}", requesterId, created.getId(), targetId);
            return toRelation(created, requesterId);
        }
        if (existing.getStatus() == Friendship.Status.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already friends");
        }
        if (existing.getRequester().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request already sent");
        }
        existing.accept();
        log.info("User id={} accepted friend request {} by adding back", requesterId, existing.getId());
        return toRelation(existing, requesterId);
    }

    /** Accepts, or deletes a rejected request so it can be sent again later. */
    @Transactional
    public void respond(String userId, String requestId, boolean accept) {
        Friendship request = friendshipRepository.findById(requestId)
                .filter(f -> f.getAddressee().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found"));
        if (request.getStatus() != Friendship.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already friends");
        }
        if (accept) {
            request.accept();
        } else {
            friendshipRepository.delete(request);
        }
        log.info("User id={} {} friend request {}", userId, accept ? "accepted" : "rejected", requestId);
    }

    /**
     * Removes a friend, or cancels a request the viewer sent (CGT-197). Incoming
     * requests are rejected through {@link #respond} instead.
     */
    @Transactional
    public void remove(String viewerId, String otherId) {
        Friendship friendship = friendshipRepository.findBetween(viewerId, otherId)
                .filter(f -> f.getStatus() == Friendship.Status.ACCEPTED
                        || f.getRequester().getId().equals(viewerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No friend or sent request to remove"));
        friendshipRepository.delete(friendship);
        log.info("User id={} removed {} with user id={}", viewerId,
                friendship.getStatus() == Friendship.Status.ACCEPTED ? "friendship" : "friend request", otherId);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> friends(String userId) {
        return friendshipRepository.findInvolving(userId, Friendship.Status.ACCEPTED).stream()
                .map(f -> f.other(userId))
                .map(u -> new FriendResponse(u.getId(), u.getDisplayName(), u.getEmail()))
                .sorted(Comparator.comparing(FriendResponse::displayName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> incomingRequests(String userId) {
        return friendshipRepository.findByAddresseeIdAndStatusOrderByCreatedAtDesc(userId, Friendship.Status.PENDING)
                .stream()
                .map(f -> new FriendRequestResponse(
                        f.getId(), f.getRequester().getId(), f.getRequester().getDisplayName(), f.getCreatedAt()))
                .toList();
    }

    private static RelationResponse toRelation(Friendship f, String viewerId) {
        if (f.getStatus() == Friendship.Status.ACCEPTED) {
            return new RelationResponse(Relation.FRIENDS, null);
        }
        boolean sent = f.getRequester().getId().equals(viewerId);
        return new RelationResponse(sent ? Relation.OUTGOING : Relation.INCOMING, f.getId());
    }
}
