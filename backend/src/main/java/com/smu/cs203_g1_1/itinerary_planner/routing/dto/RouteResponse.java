package com.smu.cs203_g1_1.itinerary_planner.routing.dto;

import java.time.OffsetDateTime;

import tools.jackson.databind.JsonNode;
import com.smu.cs203_g1_1.itinerary_planner.routing.RouteMode;

public record RouteResponse(
        String fromActivityId,
        String toActivityId,
        String fromLocationName,
        String toLocationName,
        RouteMode mode,
        OffsetDateTime departureDateTime,
        JsonNode routeData
) {
}