package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import jakarta.persistence.*;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;

    /*
     * Many trips belong to one User (the owner).
     * Database: trips.owner_id -> users.id
     */
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties("trips")
    private User owner;

    /*
     * Many-to-many membership.
     *
     * Trip is the owning side. User.java declares
     *     @ManyToMany(mappedBy = "users")
     * so this field MUST stay named "users".
     */
    @ManyToMany
    @JoinTable(
            name = "trip_members",
            joinColumns = @JoinColumn(name = "trip_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @JsonIgnoreProperties("trips")
    private List<User> users = new ArrayList<>();

    /*
     * One trip has many activities. Activity owns the foreign key
     * (activities.trip_id), so this side is mappedBy = "trip".
     */
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Activity> itinerary = new ArrayList<>();

    // Required by JPA.
    protected Trip() {
    }

    public Trip(String name, LocalDate startDate, LocalDate endDate, User owner) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<User> getUsers() {
        return users;
    }

    public List<Activity> getItinerary() {
        return itinerary;
    }

    public void addMember(User user) {
        if (!users.contains(user)) {
            users.add(user);
        }
    }

    public void removeMember(User user) {
        users.remove(user);
    }

    public void addActivity(Activity activity) {
        itinerary.add(activity);
        activity.setTrip(this);
    }

    public void removeActivity(Activity activity) {
        itinerary.remove(activity);
        // activities.trip_id is NOT NULL, so the back-reference is left intact.
    }
}
