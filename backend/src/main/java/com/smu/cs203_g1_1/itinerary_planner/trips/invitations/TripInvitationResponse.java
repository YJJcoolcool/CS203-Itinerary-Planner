package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

import java.time.Instant;
import java.time.LocalDate;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;

// Flat view of an invite for the frontend; avoids serializing full User/Trip graphs (no emails).
public record TripInvitationResponse(
        String id,
        String tripId,
        String tripName,
        LocalDate tripStartDate,
        LocalDate tripEndDate,
        String senderDisplayName,
        String receiverDisplayName,
        InvitationStatus status,
        Instant createdAt) {

    public static TripInvitationResponse from(TripInvitation invitation) {
        Trip trip = invitation.getTrip();
        return new TripInvitationResponse(
                invitation.getId(),
                trip.getId(),
                trip.getName(),
                trip.getStartDate(),
                trip.getEndDate(),
                invitation.getSender().getDisplayName(),
                invitation.getReceiver().getDisplayName(),
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
