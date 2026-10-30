package com.smu.cs203_g1_1.itinerary_planner.routing;

import java.time.OffsetDateTime;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smu.cs203_g1_1.itinerary_planner.routing.dto.RouteResponse;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@RestController
@RequestMapping("/api/trips/{tripId}/activities")
public class RoutingController {

    private final RoutingService routingService;
    private final UserService userService;

    public RoutingController(
            RoutingService routingService,
            UserService userService) {

        this.routingService = routingService;
        this.userService = userService;
    }

    /*
     * GET /api/trips/{tripId}/activities/{fromActivityId}/route-to/{toActivityId}
     */
    @GetMapping("/{fromActivityId}/route-to/{toActivityId}")
    public RouteResponse getRoute(
            @AuthenticationPrincipal Jwt jwt,

            @PathVariable String tripId,

            @PathVariable String fromActivityId,

            @PathVariable String toActivityId,

            @RequestParam RouteMode mode,

            @RequestParam(required = false)
            OffsetDateTime departureDateTime) {

        return routingService.getRoute(
                userService.getCurrentUserId(jwt),
                tripId,
                fromActivityId,
                toActivityId,
                mode,
                departureDateTime);
    }
}