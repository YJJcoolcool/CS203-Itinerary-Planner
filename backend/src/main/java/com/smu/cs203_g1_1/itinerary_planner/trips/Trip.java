package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties({"trips", "role"})
    private User owner;

    /*
     * Membership with a role (OWNER / EDITOR / VIEWER). The owner also has
     * a row here with role OWNER so every access check is one lookup.
     */
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Activity> itinerary = new ArrayList<>();

    protected Trip() {
    }

    public Trip(String name, LocalDate startDate, LocalDate endDate, User owner) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.owner = owner;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public User getOwner() { return owner; }
    public List<TripMember> getMembers() { return members; }
    public List<Activity> getItinerary() { return itinerary; }

    public boolean isOwner(User user) {
        return owner != null && user != null && owner.getId().equals(user.getId());
    }

    public Optional<TripMember> findMember(User user) {
        return members.stream()
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .findFirst();
    }

    /** Adds the user with the given role. OWNER is only valid for the trip's owner. */
    public TripMember addMember(User user, TripRole role) {
        if (role == TripRole.OWNER && !isOwner(user)) {
            throw new IllegalArgumentException("A trip can only have one owner");
        }
        if (role != TripRole.OWNER && isOwner(user)) {
            throw new IllegalArgumentException("The trip owner must keep the OWNER role");
        }
        TripMember member = new TripMember(this, user, role);
        members.add(member);
        return member;
    }

    public void removeMember(User user) {
        members.removeIf(m -> m.getUser().getId().equals(user.getId()));
    }

    public void addActivity(Activity activity) {
        itinerary.add(activity);
        activity.setTrip(this);
    }

    public void removeActivity(Activity activity) {
        itinerary.remove(activity); // orphanRemoval deletes the row
    }
}