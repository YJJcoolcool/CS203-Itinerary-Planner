package com.smu.cs203_g1_1.itinerary_planner.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
        return userService.findOrCreateCurrentUser(
                jwt,
                request.displayName());
    }

    @GetMapping("/api/users/me")
    public User getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(jwt);
    }

    @PatchMapping("/api/users/me")
    public User updateDisplayName(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateDisplayNameRequest request) {
        return userService.updateDisplayName(
                userService.getCurrentUser(jwt),
                request.displayName());
    }

    // ----- Admin: /api/admin/users (the service rejects non-admins with 403) -----

    @GetMapping("/api/admin/users")
    public List<AdminUserSummary> getAllUsers(@AuthenticationPrincipal Jwt jwt) {
        return userService.getAllUsers(userService.getCurrentUser(jwt));
    }

    @PatchMapping("/api/admin/users/{userId}")
    public AdminUserSummary adminUpdateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userId") String userId,
            @RequestBody AdminUpdateUserRequest request) {
        return userService.adminUpdateUser(userService.getCurrentUser(jwt), userId, request);
    }

    @DeleteMapping("/api/admin/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminDeleteUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("userId") String userId) {
        userService.adminDeleteUser(userService.getCurrentUser(jwt), userId);
    }
}