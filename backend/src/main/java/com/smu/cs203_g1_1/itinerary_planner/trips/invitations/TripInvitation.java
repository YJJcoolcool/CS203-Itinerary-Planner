package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

import java.time.Instant;

import org.springframework.data.domain.Persistable;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import jakarta.persistence.*;

@Entity
@Table(name = "trip_invitations")
public class TripInvitation implements Persistable<String> {

    // 8-char ID assigned by TripInvitationService via util.ID, like other entities.
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

    // Role the receiver gets on acceptance. OWNER is never valid here (see Trip#invite).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripRole role = TripRole.VIEWER;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant respondedAt;

    // Required by JPA.
    protected TripInvitation() {
    }

    // Use Trip#invite(...) instead; public only because Trip is in another package.
    public TripInvitation(Trip trip, User sender, User receiver) {
        this(trip, sender, receiver, TripRole.VIEWER);
    }

    public TripInvitation(Trip trip, User sender, User receiver, TripRole role) {
        this.trip = trip;
        this.sender = sender;
        this.receiver = receiver;
        this.role = role == null ? TripRole.VIEWER : role;
    }

    @PrePersist
    void onCreate() {
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

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // ID is assigned before save(), so Spring can't use id == null; without this, save() merges a copy.
    // See ../decisions.md §16.
    @Override
    public boolean isNew() {
        return createdAt == null;
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

    public TripRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
