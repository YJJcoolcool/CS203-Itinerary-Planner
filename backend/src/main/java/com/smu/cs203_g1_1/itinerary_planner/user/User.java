package com.smu.cs203_g1_1.itinerary_planner.user;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(length = 8, nullable = false)
    private String id;

    @Column(unique = true, nullable = false)
    private String clerkUserId;

    private String displayName;

    // Synced from the Clerk JWT "email" claim; used to look up invitees.
    // Nullable so users created before this field (or without the claim) still work.
    @Column(unique = true)
    private String email;

    @ManyToMany(mappedBy = "users")
    private List<Trip> trips = new ArrayList<>();

    protected User() {
        // Required by JPA.
    }

    public User(String clerkUserId, String displayName) {
        this.clerkUserId = clerkUserId;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClerkUserId() {
        return clerkUserId;
    }

    public void setClerkUserId(String clerkUserId) {
        this.clerkUserId = clerkUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<Trip> getTrips() {
        return trips;
    }

    // Application-level role. The column default keeps existing rows valid.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'USER'")
    private AppRole role = AppRole.USER;

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
    }

    @JsonIgnore
    public boolean isAdmin() {
        return role == AppRole.ADMIN;
    }
}
