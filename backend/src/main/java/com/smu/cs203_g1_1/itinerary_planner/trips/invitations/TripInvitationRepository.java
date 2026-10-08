package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripInvitationRepository extends JpaRepository<TripInvitation, String> {

    // NOTE: tripId/userId are Long for now. When User/Trip migrate to 8-char String IDs
    // (groupmate's ID task), change these params (and service/controller signatures) to String.
    // TODO(id-migration)

    // Invitee inbox, newest first.
    List<TripInvitation> findByReceiverIdAndStatusOrderByCreatedAtDesc(Long receiverId, InvitationStatus status);

    // Owner's view of a trip's pending invites.
    List<TripInvitation> findByTripIdAndStatus(Long tripId, InvitationStatus status);

    // Blocks duplicate pending invites for the same person on the same trip.
    boolean existsByTripIdAndReceiverIdAndStatus(Long tripId, Long receiverId, InvitationStatus status);
}
