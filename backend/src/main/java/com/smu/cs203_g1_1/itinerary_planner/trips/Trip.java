package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitation;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @Column(length = 8, nullable = false)
    private String id;

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;

    /*
     * Many trips belong to one User (the owner).
     * Database: trips.owner_id -> users.id
     *
     * There is exactly one owner per trip (single non-null FK), so the
     * OWNER role can never belong to anyone else.
     */
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnoreProperties({"trips", "role"})
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
    @JsonIgnoreProperties({"trips", "role"})
    private List<User> users = new ArrayList<>();

    /*
     * Role of each member, keyed by user id (OWNER / EDITOR / VIEWER).
     * Stored in its own table (trip_member_roles) so the existing
     * trip_members join table and User.trips stay exactly as they were.
     * A member with no entry here (e.g. rows created before roles existed)
     * is treated as VIEWER, see findRole().
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_member_roles", joinColumns = @JoinColumn(name = "trip_id"))
    @MapKeyColumn(name = "user_id", length = 8)
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Map<String, TripRole> roles = new HashMap<>();

    /*
     * One trip has many activities. Activity owns the foreign key
     * (activities.trip_id), so this side is mappedBy = "trip".
     */
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Activity> itinerary = new ArrayList<>();

    // cascade so deleting a trip removes its invites (FK would block it otherwise).
    // No orphanRemoval: cancelling is a status change, not a delete.
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<TripInvitation> invitations = new ArrayList<>();

    // Required by JPA.
    protected Trip() {
    }

    public Trip(String name, LocalDate startDate, LocalDate endDate, User owner) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    // userId -> role, so the frontend can show who can do what.
    public Map<String, TripRole> getRoles() {
        return Collections.unmodifiableMap(roles);
    }

    public List<Activity> getItinerary() {
        return itinerary;
    }

    // ------------------------------------------------------------------
    // Membership and roles
    // ------------------------------------------------------------------

    public boolean isOwner(User user) {
        return owner != null && user != null
                && owner.getId() != null && owner.getId().equals(user.getId());
    }

    public boolean hasMember(User user) {
        return user != null && users.stream()
                .anyMatch(u -> u.getId() != null && u.getId().equals(user.getId()));
    }

    /** The user's role on this trip, or empty if they are not part of it. */
    public Optional<TripRole> findRole(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        if (owner != null && userId.equals(owner.getId())) {
            return Optional.of(TripRole.OWNER);
        }
        boolean member = users.stream().anyMatch(u -> userId.equals(u.getId()));
        return member
                ? Optional.of(roles.getOrDefault(userId, TripRole.VIEWER))
                : Optional.empty();
    }

    /*
     * Original signature, kept so existing callers (e.g. invitation acceptance)
     * keep compiling. No-op if already a member. The owner gets OWNER,
     * everyone else VIEWER.
     */
    public void addMember(User user) {
        if (!hasMember(user)) {
            addMember(user, isOwner(user) ? TripRole.OWNER : TripRole.VIEWER);
        }
    }

    /** Adds the user with the given role. OWNER is only valid for the trip's owner. */
    public void addMember(User user, TripRole role) {
        if (role == TripRole.OWNER && !isOwner(user)) {
            throw new IllegalArgumentException("A trip can only have one owner");
        }
        if (role != TripRole.OWNER && isOwner(user)) {
            throw new IllegalArgumentException("The trip owner must keep the OWNER role");
        }
        if (!hasMember(user)) {
            users.add(user);
        }
        roles.put(user.getId(), role);
    }

    /** Changes an existing member's role (never to or from OWNER). */
    public void setMemberRole(User user, TripRole role) {
        if (role == TripRole.OWNER || isOwner(user)) {
            throw new IllegalArgumentException("The owner role cannot be assigned or changed");
        }
        if (!hasMember(user)) {
            throw new IllegalArgumentException("User is not a member of this trip");
        }
        roles.put(user.getId(), role);
    }

    public void removeMember(User user) {
        users.removeIf(u -> u.getId() != null && u.getId().equals(user.getId()));
        roles.remove(user.getId());
    }

    public void addActivity(Activity activity) {
        itinerary.add(activity);
        activity.setTrip(this);
    }

    public void removeActivity(Activity activity) {
        itinerary.remove(activity);
        // activities.trip_id is NOT NULL, so the back-reference is left intact.
    }

    public List<TripInvitation> getInvitations() {
        return invitations;
    }

    // Only way to create an invite: guarantees invitation.trip == this and that it is in
    // this list, so both sides of the relationship stay in sync (no setTrip on purpose).
    public TripInvitation invite(User sender, User receiver) {
        return invite(sender, receiver, TripRole.VIEWER);
    }

    // Role is the membership the receiver gets upon accepting (VIEWER or EDITOR).
    public TripInvitation invite(User sender, User receiver, TripRole role) {
        TripInvitation invitation = new TripInvitation(this, sender, receiver, role);
        invitations.add(invitation);
        return invitation;
    }
}