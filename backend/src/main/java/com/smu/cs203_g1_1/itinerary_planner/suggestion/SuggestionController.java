package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
@RequestMapping("/api")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final UserService userService;

    public SuggestionController(SuggestionService suggestionService, UserService userService) {
        this.suggestionService = suggestionService;
        this.userService = userService;
    }

    public record ReplanRequest(ZonedDateTime at) {
    }

    @GetMapping("/trips/{tripId}/suggestions")
    public List<SuggestionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable("tripId") String tripId) {
        return suggestionService.list(tripId, userService.getCurrentUserId(jwt));
    }

    @PostMapping("/suggestions/{suggestionId}/accept")
    public SuggestionResponse accept(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable("suggestionId") String suggestionId) {
        return suggestionService.accept(suggestionId, userService.getCurrentUserId(jwt));
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    public SuggestionResponse reject(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable("suggestionId") String suggestionId) {
        return suggestionService.reject(suggestionId, userService.getCurrentUserId(jwt));
    }

    @PostMapping("/trips/{tripId}/replan")
    public List<SuggestionResponse> replan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("tripId") String tripId,
            @RequestBody(required = false) ReplanRequest request) {
        return suggestionService.replan(tripId, userService.getCurrentUserId(jwt),
                request == null ? null : request.at());
    }
}
