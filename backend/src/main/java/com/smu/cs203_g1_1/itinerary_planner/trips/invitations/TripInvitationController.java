package com.smu.cs203_g1_1.itinerary_planner.trips.invitations;

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
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
public class TripInvitationController {

    private final TripInvitationService invitationService;
    private final UserService userService;

    public TripInvitationController(TripInvitationService invitationService, UserService userService) {
        this.invitationService = invitationService;
        this.userService = userService;
    }

    // role is optional: EDITOR or VIEWER; missing means VIEWER, OWNER is rejected with 400.
    public record SendInvitationRequest(String email, TripRole role) { }

    public record RespondInvitationRequest(InvitationStatus status) { }

    // ----- Owner side: /api/trips/{tripId}/invitations -----

    @PostMapping("/api/trips/{tripId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TripInvitationResponse sendInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @RequestBody SendInvitationRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return invitationService.sendInvitation(
                tripId, userService.getCurrentUserId(jwt), request.email(), request.role());
    }

    @GetMapping("/api/trips/{tripId}/invitations")
    public List<TripInvitationResponse> getTripInvitations(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId) {
        return invitationService.getPendingForTrip(tripId, userService.getCurrentUserId(jwt));
    }

    @DeleteMapping("/api/trips/{tripId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @PathVariable("invitationId") String invitationId) {
        invitationService.cancel(tripId, invitationId, userService.getCurrentUserId(jwt));
    }

    // ----- Invitee side: /api/users/me/invitations -----

    @GetMapping("/api/users/me/invitations")
    public List<TripInvitationResponse> getMyInvitations(@AuthenticationPrincipal Jwt jwt) {
        return invitationService.getPendingForUser(userService.getCurrentUserId(jwt));
    }

    @PatchMapping("/api/users/me/invitations/{invitationId}")
    public TripInvitationResponse respondToInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("invitationId") String invitationId,
            @RequestBody RespondInvitationRequest request) {
        if (request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        return invitationService.respond(invitationId, userService.getCurrentUserId(jwt), request.status());
    }
}