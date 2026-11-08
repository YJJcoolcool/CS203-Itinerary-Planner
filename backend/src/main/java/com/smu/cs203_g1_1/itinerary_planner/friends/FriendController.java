package com.smu.cs203_g1_1.itinerary_planner.friends;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.friends.FriendService.FriendRequestResponse;
import com.smu.cs203_g1_1.itinerary_planner.friends.FriendService.FriendResponse;
import com.smu.cs203_g1_1.itinerary_planner.friends.FriendService.RelationResponse;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
public class FriendController {

    private final FriendService friendService;
    private final UserService userService;

    public FriendController(FriendService friendService, UserService userService) {
        this.friendService = friendService;
        this.userService = userService;
    }

    public record SendFriendRequest(String userId) { }

    public record RespondFriendRequest(boolean accept) { }

    @GetMapping("/api/users/me/friends")
    public List<FriendResponse> getFriends(@AuthenticationPrincipal Jwt jwt) {
        return friendService.friends(userService.getCurrentUserId(jwt));
    }

    @GetMapping("/api/users/me/friend-requests")
    public List<FriendRequestResponse> getFriendRequests(@AuthenticationPrincipal Jwt jwt) {
        return friendService.incomingRequests(userService.getCurrentUserId(jwt));
    }

    @PostMapping("/api/users/me/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RelationResponse sendFriendRequest(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody SendFriendRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        return friendService.sendRequest(userService.getCurrentUserId(jwt), request.userId());
    }

    @PatchMapping("/api/users/me/friend-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void respondToFriendRequest(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable("requestId") String requestId,
                                       @RequestBody RespondFriendRequest request) {
        friendService.respond(userService.getCurrentUserId(jwt), requestId, request.accept());
    }

    /** The viewer's relation to another user, for the profile popup's friend button. */
    @GetMapping("/api/users/{userId}/friendship")
    public RelationResponse getFriendship(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable("userId") String userId) {
        return friendService.relation(userService.getCurrentUserId(jwt), userId);
    }
}
