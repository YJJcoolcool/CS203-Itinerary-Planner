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

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

// TODO(id-migration): tripId path variables are Long for now.
@RestController
public class TripInvitationController {

    private final TripInvitationService invitationService;
    private final UserService userService;

    public TripInvitationController(TripInvitationService invitationService, UserService userService) {
        this.invitationService = invitationService;
        this.userService = userService;
    }

    public record SendInvitationRequest(String email) { }

    public record RespondInvitationRequest(InvitationStatus status) { }

    // ----- Owner side: /api/trips/{tripId}/invitations -----

    @PostMapping("/api/trips/{tripId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TripInvitationResponse sendInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @RequestBody SendInvitationRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return invitationService.sendInvitation(tripId, currentUserId(jwt), request.email());
    }

    @GetMapping("/api/trips/{tripId}/invitations")
    public List<TripInvitationResponse> getTripInvitations(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId) {
        return invitationService.getPendingForTrip(tripId, currentUserId(jwt));
    }

    @DeleteMapping("/api/trips/{tripId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") Long tripId,
            @PathVariable("invitationId") String invitationId) {
        invitationService.cancel(tripId, invitationId, currentUserId(jwt));
    }

    // ----- Invitee side: /api/users/me/invitations -----

    @GetMapping("/api/users/me/invitations")
    public List<TripInvitationResponse> getMyInvitations(@AuthenticationPrincipal Jwt jwt) {
        return invitationService.getPendingForUser(currentUserId(jwt));
    }

    @PatchMapping("/api/users/me/invitations/{invitationId}")
    public TripInvitationResponse respondToInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("invitationId") String invitationId,
            @RequestBody RespondInvitationRequest request) {
        if (request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        return invitationService.respond(invitationId, currentUserId(jwt), request.status());
    }

    // Resolve the Clerk JWT to our internal user id once, per docs/authenticated-endpoints.md.
    private Long currentUserId(Jwt jwt) {
        return userService.getUserByClerkId(jwt.getSubject()).getId();
    }
}
