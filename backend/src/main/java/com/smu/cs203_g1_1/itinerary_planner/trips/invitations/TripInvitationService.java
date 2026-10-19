package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

@Service
public class TripInvitationService {

    private static final Logger log = LoggerFactory.getLogger(TripInvitationService.class);

    private final TripInvitationRepository invitationRepository;
    private final TripService tripService;
    private final UserService userService;

    public TripInvitationService(TripInvitationRepository invitationRepository,
            TripService tripService,
            UserService userService) {
        this.invitationRepository = invitationRepository;
        this.tripService = tripService;
        this.userService = userService;
    }

    // Default invitation; no role means VIEWER.
    @Transactional
    public TripInvitationResponse sendInvitation(String tripId, String senderId, String receiverEmail) {
        return sendInvitation(tripId, senderId, receiverEmail, null);
    }

    @Transactional
    public TripInvitationResponse sendInvitation(String tripId, String senderId, String receiverEmail, TripRole role) {
        // Checked first so a bad request never touches the database.
        if (role == TripRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip can only have one owner");
        }
        TripRole assigned = (role == null) ? TripRole.VIEWER : role;

        Trip trip = tripService.getTrip(tripId);
        requireCanManageInvitations(trip, senderId);
        // Looked up (not trip.getOwner()) so it stays correct when non-owners can invite.
        User sender = userService.getUserById(senderId);
        User receiver = userService.getUserByEmail(receiverEmail);

        if (receiver.getId().equals(senderId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot invite yourself");
        }
        if (isMember(trip, receiver)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this trip");
        }
        if (invitationRepository.existsByTripIdAndReceiverIdAndStatus(tripId, receiver.getId(), InvitationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a pending invite to this trip");
        }


        String id = ID.generateUniqueID(invitationRepository::existsById);
        TripInvitation invitation = trip.invite(sender, receiver, assigned);
        invitation.setId(id);
        TripInvitation saved = invitationRepository.save(invitation);
        log.info("User id={} invited user id={} to trip id={} as {} (invite {})",
                senderId, receiver.getId(), tripId, assigned, saved.getId());
        return TripInvitationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TripInvitationResponse> getPendingForUser(String userId) {
        return invitationRepository
                .findByReceiverIdAndStatusOrderByCreatedAtDesc(userId, InvitationStatus.PENDING)
                .stream()
                .map(TripInvitationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TripInvitationResponse> getPendingForTrip(String tripId, String requesterId) {
        requireCanManageInvitations(tripService.getTrip(tripId), requesterId);
        return invitationRepository.findByTripIdAndStatus(tripId, InvitationStatus.PENDING)
                .stream()
                .map(TripInvitationResponse::from)
                .toList();
    }

    @Transactional
    public TripInvitationResponse respond(String invitationId, String userId, InvitationStatus decision) {
        TripInvitation invitation = getInvitation(invitationId);
        if (!invitation.getReceiver().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation is not addressed to you");
        }
        switch (decision) {
            case ACCEPTED -> {
                transition(invitation::accept);
                // The role the owner picked when inviting, not always VIEWER.
                invitation.getTrip().addMember(invitation.getReceiver(), invitation.getRole());
            }
            case DECLINED ->
                transition(invitation::decline);
            default ->
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACCEPTED or DECLINED");
        }
        log.info("User id={} {} invite {}", userId, decision, invitationId);
        return TripInvitationResponse.from(invitation);
    }

    @Transactional
    public void cancel(String tripId, String invitationId, String requesterId) {
        TripInvitation invitation = getInvitation(invitationId);
        // Invite must belong to the trip in the URL, so /trips/A/invitations/{inviteOfB} is a 404.
        if (!invitation.getTrip().getId().equals(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        requireCanManageInvitations(invitation.getTrip(), requesterId);
        transition(invitation::cancel);
        log.info("User id={} cancelled invite {}", requesterId, invitationId);
    }

    private TripInvitation getInvitation(String invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
    }

    // Who may send/view/cancel invites. Currently owner-only; extend here when roles land.
    private void requireCanManageInvitations(Trip trip, String userId) {
        if (!trip.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner can manage invitations");
        }
    }

    // Compare by id: User has no equals(), so contains() would rely on object identity.
    private boolean isMember(Trip trip, User user) {
        return trip.getUsers().stream().anyMatch(u -> u.getId().equals(user.getId()));
    }

    // Entity throws IllegalStateException when the invite is no longer PENDING; surface it as 409.
    private void transition(Runnable change) {
        try {
            change.run();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
