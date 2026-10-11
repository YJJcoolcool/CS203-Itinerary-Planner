package com.smu.cs203_g1_1.itinerary_planner.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return userRepository.findByClerkUserId(clerkUserId)
                .map(existing -> {
                    log.debug("Found existing user for clerk id {}", clerkUserId);
                    // Keep email in sync in case the user changed it in Clerk.
                    if (normalizedEmail != null && !normalizedEmail.equals(existing.getEmail())) {
                        existing.setEmail(normalizedEmail);
                        return userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    String id = ID.generateUniqueID(userRepository::existsById);

                    User created = new User(clerkUserId, displayName);
                    created.setId(id);
                    created.setEmail(normalizedEmail);

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
}
