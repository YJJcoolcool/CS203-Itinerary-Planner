package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripInvitationRepository extends JpaRepository<TripInvitation, String> {

    // Invitee inbox, newest first.
    List<TripInvitation> findByReceiverIdAndStatusOrderByCreatedAtDesc(String receiverId, InvitationStatus status);

    // Owner's view of a trip's pending invites.
    List<TripInvitation> findByTripIdAndStatus(String tripId, InvitationStatus status);

    // Blocks duplicate pending invites for the same person on the same trip.
    boolean existsByTripIdAndReceiverIdAndStatus(String tripId, String receiverId, InvitationStatus status);

    // Every invite a user sent or received (used when an admin deletes that user).
    List<TripInvitation> findBySenderIdOrReceiverId(String senderId, String receiverId);
}