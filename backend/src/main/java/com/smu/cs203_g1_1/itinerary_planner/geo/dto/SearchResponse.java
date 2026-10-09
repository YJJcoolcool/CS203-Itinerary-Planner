package com.smu.cs203_g1_1.itinerary_planner.geo.dto;

import java.util.List;

/** Envelope for list-valued geo responses: the results plus the OSM attribution text. */
public record SearchResponse(List<PlaceDto> results, String attribution) {
}
