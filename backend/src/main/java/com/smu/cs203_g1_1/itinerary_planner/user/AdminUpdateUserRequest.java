package com.smu.cs203_g1_1.itinerary_planner.user;

/**
 * Body of PATCH /api/admin/users/{userId}. These are the only fields an admin
 * may change; null means "leave unchanged". id, clerkUserId and email are
 * deliberately absent (email is synced from Clerk on login).
 */
public record AdminUpdateUserRequest(String displayName, AppRole role) {
}