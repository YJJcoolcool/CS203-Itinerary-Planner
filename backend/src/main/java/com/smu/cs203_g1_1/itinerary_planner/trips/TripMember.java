package com.smu.cs203_g1_1.itinerary_planner.trips;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import jakarta.persistence.*;

@Entity
@Table(
        name = "trip_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id")
    @JsonIgnore
    private Trip trip;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"trips", "role"})
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripRole role;

    protected TripMember() {
    }

    public TripMember(Trip trip, User user, TripRole role) {
        this.trip = trip;
        this.user = user;
        this.role = role;
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public User getUser() { return user; }
    public TripRole getRole() { return role; }
    public void setRole(TripRole role) { this.role = role; }
}