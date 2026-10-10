package com.smu.cs203_g1_1.itinerary_planner.user;
 
import java.util.ArrayList;
import java.util.List;
 
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripMember;
 
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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

    
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private List<TripMember> memberships = new ArrayList<>();

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
   
    @JsonIgnore
    public List<Trip> getTrips() {
        return memberships.stream().map(TripMember::getTrip).toList();
    }
}
