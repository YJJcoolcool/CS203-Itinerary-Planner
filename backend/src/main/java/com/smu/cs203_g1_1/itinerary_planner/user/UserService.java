package com.smu.cs203_g1_1.itinerary_planner.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

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

    public String getCurrentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return getUserByClerkId(jwt.getSubject()).getId();
    }

    public User findOrCreateUser(String clerkUserId, String displayName) {
        return userRepository.findByClerkUserId(clerkUserId)
                .map(existing -> {
                    log.debug("Found existing user for clerk id {}", clerkUserId);
                    return existing;
                })
                .orElseGet(() -> {
                    String id = ID.generateUniqueID(userRepository::existsById);

                    User created = new User(clerkUserId, displayName);
                    created.setId(id);

                    User saved = userRepository.save(created);

                    log.info("Created user id={} for clerk id {}", saved.getId(), clerkUserId);
                    return saved;
                });
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

    /*
     * TODO: Uncomment after the Trip entity is added.
     *
     * public List<Trip> getUserTrips(Long userId) {
     *     return getUserById(userId).getTrips();
     * }
     */
}
