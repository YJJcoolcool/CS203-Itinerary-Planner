package com.smu.cs203_g1_1.itinerary_planner.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public User findOrCreateUser(String clerkUserId, String displayName) {
        return userRepository.findByClerkUserId(clerkUserId)
                .map(existing -> {
                    log.debug("Found existing user for clerk id {}", clerkUserId);
                    return existing;
                })
                .orElseGet(() -> {
                    User created = userRepository.save(new User(clerkUserId, displayName));
                    log.info("Created user id={} for clerk id {}", created.getId(), clerkUserId);
                    return created;
                });
    }

    public User getUserById(long id){
        return userRepository.findById(id).orElseThrow(() ->new UserNotFoundException("User not found"));
    }

    public User updateDisplayName(long id, String displayName) {
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
