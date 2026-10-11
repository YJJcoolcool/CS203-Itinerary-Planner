package com.smu.cs203_g1_1.itinerary_planner.user;

/**
 * What the admin endpoints return for a user. Deliberately not the User entity,
 * so the response doesn't drag in the user's trips (and their members, activities...).
 */
public record AdminUserSummary(String id, String displayName, String email, AppRole role) {

    public static AdminUserSummary from(User user) {
        return new AdminUserSummary(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole());
    }
}