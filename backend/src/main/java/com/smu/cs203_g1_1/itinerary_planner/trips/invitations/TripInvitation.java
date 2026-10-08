package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

import java.time.Instant;

import com.smu.cs203_g1_1.itinerary_planner.common.ShortId;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import jakarta.persistence.*;

@Entity
@Table(name = "trip_invitations")
public class TripInvitation {

    // 8-char alphanumeric ID from ShortId (jnanoid).
    // TODO(id-migration): switch to groupmate's shared ID generator once it lands.
    @Id
    @Column(length = 8)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant respondedAt;

    // Required by JPA.
    protected TripInvitation() {
    }

    // Use Trip#invite(...) instead; public only because Trip is in another package.
    public TripInvitation(Trip trip, User sender, User receiver) {
        this.trip = trip;
        this.sender = sender;
        this.receiver = receiver;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = ShortId.generate();
        }
        createdAt = Instant.now();
    }

    public void accept() {
        transitionTo(InvitationStatus.ACCEPTED);
    }

    public void decline() {
        transitionTo(InvitationStatus.DECLINED);
    }

    public void cancel() {
        transitionTo(InvitationStatus.CANCELLED);
    }

    // Only PENDING invites can change state; the service maps this exception to 409.
    private void transitionTo(InvitationStatus next) {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is already " + status);
        }
        status = next;
        respondedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public User getSender() {
        return sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
