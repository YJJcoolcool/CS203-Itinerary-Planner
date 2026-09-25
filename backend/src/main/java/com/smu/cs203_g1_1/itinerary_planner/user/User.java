package com.smu.cs203_g1_1.itinerary_planner.user;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import jakarta.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.List;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String clerkUserId;

    private String displayName;

    
    @ManyToMany(mappedBy = "users")
    private List<Trip> trips = new ArrayList<>();

    protected User() {
        // Required by JPA.
    }

    public User(String clerkUserId, String displayName) {
        this.clerkUserId = clerkUserId;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
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
   
    public List<Trip> getTrips() {
        return trips;
    }
}
