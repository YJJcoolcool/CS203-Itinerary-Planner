package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smu.cs203_g1_1.itinerary_planner.ratelimit.AiRateLimited;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

/**
 * Preference endpoints.
 *
 * <p>Another user's preference is readable (structured fields only); notes are
 * omitted from that view. Trip preferences are member-readable and
 * owner/editor-writable.</p>
 */
@RestController
@RequestMapping("/api")
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final UserService userService;

    public PreferenceController(PreferenceService preferenceService, UserService userService) {
        this.preferenceService = preferenceService;
        this.userService = userService;
    }

    public record UserPreferenceResponse(String userId,
                                         List<String> tags,
                                         BigDecimal maxWalkKm,
                                         String dietary,
                                         String indoorPreference,
                                         String summary,
                                         String notes,
                                         Instant updatedAt) {

        static UserPreferenceResponse self(UserPreference preference) {
            return new UserPreferenceResponse(
                preference.getUserId(),
                preference.getTags(),
                preference.getMaxWalkKm(),
                preference.getDietary(),
                preference.getIndoorPreference(),
                preference.getSummary(),
                preference.getNotes(),
                preference.getUpdatedAt());
        }

        /** Other users never see free-text notes. */
        static UserPreferenceResponse publicView(UserPreference preference) {
            return new UserPreferenceResponse(
                preference.getUserId(),
                preference.getTags(),
                preference.getMaxWalkKm(),
                preference.getDietary(),
                preference.getIndoorPreference(),
                preference.getSummary(),
                null,
                preference.getUpdatedAt());
        }
    }

    public record UpdateUserPreferenceRequest(List<String> tags,
                                              BigDecimal maxWalkKm,
                                              String dietary,
                                              String indoorPreference,
                                              String notes) {
    }

    public record TripPreferenceResponse(String tripId,
                                         List<String> goals,
                                         List<String> tags,
                                         String summary,
                                         boolean summaryStale,
                                         String updatedBy,
                                         Instant updatedAt) {

        static TripPreferenceResponse from(TripPreference preference) {
            return new TripPreferenceResponse(
                preference.getTripId(),
                preference.getGoals(),
                preference.getTags(),
                preference.getSummary(),
                preference.isSummaryStale(),
                preference.getUpdatedBy(),
                preference.getUpdatedAt());
        }
    }

    public record UpdateSummaryRequest(String summary) {
    }

    public record UpdateTripPreferenceRequest(List<String> goals, List<String> tags) {
    }

    @GetMapping("/users/me/preferences")
    public UserPreferenceResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return UserPreferenceResponse.self(
            preferenceService.getOrCreateUser(userService.getCurrentUserId(jwt)));
    }

    @PutMapping("/users/me/preferences")
    public UserPreferenceResponse updateMine(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody UpdateUserPreferenceRequest request) {
        return UserPreferenceResponse.self(preferenceService.updateUser(
            userService.getCurrentUserId(jwt),
            request.tags(),
            request.maxWalkKm(),
            request.dietary(),
            request.indoorPreference(),
            request.notes()));
    }

    @AiRateLimited
    @PostMapping("/users/me/preferences/summarise")
    public UserPreferenceResponse summariseMine(@AuthenticationPrincipal Jwt jwt) {
        return UserPreferenceResponse.self(
            preferenceService.summariseUser(userService.getCurrentUserId(jwt)));
    }

    @PutMapping("/users/me/preferences/summary")
    public UserPreferenceResponse updateMySummary(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestBody UpdateSummaryRequest request) {
        return UserPreferenceResponse.self(preferenceService.updateUserSummary(
            userService.getCurrentUserId(jwt), request.summary()));
    }

    @GetMapping("/users/{userId}/preferences")
    public UserPreferenceResponse byUser(@PathVariable("userId") String userId) {
        return UserPreferenceResponse.publicView(preferenceService.readUser(userId));
    }

    @GetMapping("/trips/{tripId}/preferences")
    public TripPreferenceResponse trip(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable("tripId") String tripId) {
        return TripPreferenceResponse.from(
            preferenceService.getOrCreateTrip(tripId, userService.getCurrentUserId(jwt)));
    }

    @PutMapping("/trips/{tripId}/preferences")
    public TripPreferenceResponse updateTrip(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable("tripId") String tripId,
                                             @RequestBody UpdateTripPreferenceRequest request) {
        return TripPreferenceResponse.from(preferenceService.updateTrip(
            tripId,
            userService.getCurrentUserId(jwt),
            request.goals(),
            request.tags()));
    }

    @AiRateLimited
    @PostMapping("/trips/{tripId}/preferences/summarise")
    public TripPreferenceResponse summarise(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable("tripId") String tripId) {
        return TripPreferenceResponse.from(
            preferenceService.summariseTrip(tripId, userService.getCurrentUserId(jwt)));
    }
}
