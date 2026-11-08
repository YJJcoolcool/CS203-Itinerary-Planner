package com.smu.cs203_g1_1.itinerary_planner.friends;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.domain.Persistable;

import com.smu.cs203_g1_1.itinerary_planner.user.User;

import jakarta.persistence.*;

/** A friend request, which becomes the friendship itself once accepted. */
@Entity
@Table(name = "friendships")
public class Friendship implements Persistable<String> {

    public enum Status { PENDING, ACCEPTED }

    @Id
    @Column(length = 8)
    private String id;

    // OnDelete mirrors the migration's cascade in the Hibernate-built test schema.
    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User requester;

    @ManyToOne(optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA.
    protected Friendship() {
    }

    public Friendship(String id, User requester, User addressee) {
        this.id = id;
        this.requester = requester;
        this.addressee = addressee;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void accept() {
        status = Status.ACCEPTED;
    }

    /** The user on the other side of this friendship from {@code userId}. */
    public User other(String userId) {
        return requester.getId().equals(userId) ? addressee : requester;
    }

    @Override
    public String getId() {
        return id;
    }

    // ID is assigned before save(), same as TripInvitation.
    @Override
    public boolean isNew() {
        return createdAt == null;
    }

    public User getRequester() {
        return requester;
    }

    public User getAddressee() {
        return addressee;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
