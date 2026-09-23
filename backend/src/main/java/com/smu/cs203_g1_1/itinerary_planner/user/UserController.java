package com.smu.cs203_g1_1.itinerary_planner.user;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

// TODO: Validate that display names are present and not blank before saving.
@RestController
public class UserController{
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/api/users/me")
    public User createCurrentUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateUserRequest request) {
        return userService.findOrCreateUser(
                jwt.getSubject(),
                request.displayName());
    }

    @GetMapping("/api/users/me")
    public User getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.getUserByClerkId(jwt.getSubject());
    }

    @PatchMapping("/api/users/me")
    public User updateDisplayName(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateDisplayNameRequest request) {
        User user = userService.getUserByClerkId(jwt.getSubject());
        return userService.updateDisplayName(
                user,
                request.displayName());
    }

    /*
     * TODO: Uncomment after the Trip entity is added.
     *
     * @GetMapping("/api/users/me/trips")
     * public List<Trip> getUserTrips(@AuthenticationPrincipal Jwt jwt) {
     *     User user = userService.getUserByClerkId(jwt.getSubject());
     *     return userService.getUserTrips(user.getId());
     * }
     */
}
