package com.smu.cs203_g1_1.itinerary_planner.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {
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
                .orElseGet(() -> userRepository.save(new User(clerkUserId, displayName)));
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
        return userRepository.save(user);
    }

    /*
     * TODO: Uncomment after the Trip entity is added.
     *
     * public List<Trip> getUserTrips(Long userId) {
     *     return getUserById(userId).getTrips();
     * }
     */
}
