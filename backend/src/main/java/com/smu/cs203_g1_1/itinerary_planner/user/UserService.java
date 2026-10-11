package com.smu.cs203_g1_1.itinerary_planner.user;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitation;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationRepository;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripInvitationRepository invitationRepository;
    // Clerk user ids that are promoted to ADMIN when they sign in (see app.admin.clerk-user-ids).
    private final Set<String> bootstrapAdminClerkIds;

    public UserService(UserRepository userRepository,
                       TripRepository tripRepository,
                       TripInvitationRepository invitationRepository,
                       @Value("${app.admin.clerk-user-ids:}") String bootstrapAdminClerkIds) {
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.invitationRepository = invitationRepository;
        this.bootstrapAdminClerkIds = Arrays.stream(bootstrapAdminClerkIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public User getUserByClerkId(String clerkUserId) {
        return userRepository.findByClerkUserId(clerkUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public User getCurrentUser(Jwt jwt) {
        return getUserByClerkId(requireToken(jwt).getSubject());
    }

    public String getCurrentUserId(Jwt jwt) {
        return getCurrentUser(jwt).getId();
    }

    public Optional<User> findCurrentUser(Jwt jwt) {
        return userRepository.findByClerkUserId(requireToken(jwt).getSubject());
    }

    public User findOrCreateCurrentUser(Jwt jwt, String displayName) {
        requireToken(jwt);
        return findOrCreateUser(jwt.getSubject(), displayName, jwt.getClaimAsString("email"));
    }

    private Jwt requireToken(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return jwt;
    }

    public User findOrCreateUser(String clerkUserId, String displayName, String email) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        boolean bootstrapAdmin = bootstrapAdminClerkIds.contains(clerkUserId);
        return userRepository.findByClerkUserId(clerkUserId)
                .map(existing -> {
                    log.debug("Found existing user for clerk id {}", clerkUserId);
                    boolean changed = false;
                    // Keep email in sync in case the user changed it in Clerk.
                    if (normalizedEmail != null && !normalizedEmail.equals(existing.getEmail())) {
                        existing.setEmail(normalizedEmail);
                        changed = true;
                    }
                    if (bootstrapAdmin && !existing.isAdmin()) {
                        existing.setRole(AppRole.ADMIN);
                        changed = true;
                    }
                    return changed ? userRepository.save(existing) : existing;
                })
                .orElseGet(() -> {
                    String id = ID.generateUniqueID(userRepository::existsById);

                    User created = new User(clerkUserId, displayName);
                    created.setId(id);
                    created.setEmail(normalizedEmail);
                    if (bootstrapAdmin) {
                        created.setRole(AppRole.ADMIN);
                    }

                    User saved = userRepository.save(created);

                    log.info("Created user id={} for clerk id {}", saved.getId(), clerkUserId);
                    return saved;
                });
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UserNotFoundException("No user with that email"));
    }

    public User getUserById(String id) {
        return userRepository.findById(id).orElseThrow(() ->new UserNotFoundException("User not found"));
    }

    public User updateDisplayName(String id, String displayName) {
        User user = getUserById(id);
        return updateDisplayName(user, displayName);
    }

    public User updateDisplayName(User user, String displayName) {
        user.setDisplayName(displayName);
        User saved = userRepository.save(user);
        log.info("Updated display name for user id={}", user.getId());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Admin operations (CGT-73 list, CGT-74 edit, CGT-75 delete, CGT-76 check)
    // ---------------------------------------------------------------------

    /** CGT-76: the one place that decides who counts as an admin. 403 for everyone else. */
    public User requireAdmin(User caller) {
        if (caller == null || !caller.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return caller;
    }

    /** CGT-73: id, display name, email and role of every user. */
    @Transactional(readOnly = true)
    public List<AdminUserSummary> getAllUsers(User caller) {
        requireAdmin(caller);
        return userRepository.findAll().stream().map(AdminUserSummary::from).toList();
    }

    /**
     * CGT-74: change a user's display name and/or role. A null field is left unchanged.
     * id, clerkUserId and email are never touched (email is owned by Clerk).
     */
    @Transactional
    public AdminUserSummary adminUpdateUser(User caller, String targetId, AdminUpdateUserRequest request) {
        requireAdmin(caller);
        User target = getUserById(targetId);

        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name cannot be blank");
            }
            target.setDisplayName(request.displayName().trim());
        }
        if (request.role() != null) {
            if (target.getId().equals(caller.getId()) && request.role() != AppRole.ADMIN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admins cannot remove their own admin role");
            }
            target.setRole(request.role());
        }

        User saved = userRepository.save(target);
        log.info("Admin id={} updated user id={}", caller.getId(), targetId);
        return AdminUserSummary.from(saved);
    }

    /**
     * CGT-75: delete a user. Refused (409) while they still own trips, because deleting
     * a trip is the owner's call, not a side effect. Their memberships and invitations are removed first.
     */
    @Transactional
    public void adminDeleteUser(User caller, String targetId) {
        requireAdmin(caller);
        User target = getUserById(targetId);

        if (target.getId().equals(caller.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admins cannot delete themselves");
        }

        List<Trip> trips = tripRepository.findDistinctByOwnerIdOrUsersId(targetId, targetId);
        for (Trip trip : trips) {
            if (trip.isOwner(target)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User still owns trips; delete or reassign them first");
            }
        }
        // Not an owner of any of these, so only a membership to drop (also clears the role).
        for (Trip trip : trips) {
            trip.removeMember(target);
            tripRepository.save(trip);
        }

        // sender_id / receiver_id are NOT NULL, so their invites must go too.
        List<TripInvitation> invitations = invitationRepository.findBySenderIdOrReceiverId(targetId, targetId);
        for (TripInvitation invitation : invitations) {
            // Trip.invitations cascades ALL; keep it in sync so the delete isn't undone on flush.
            invitation.getTrip().getInvitations().remove(invitation);
        }
        invitationRepository.deleteAll(invitations);

        userRepository.delete(target);
        log.info("Admin id={} deleted user id={}", caller.getId(), targetId);
    }
}