package com.smu.cs203_g1_1.itinerary_planner.geo.overpass;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw Overpass response wrapper containing the returned elements. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OverpassResponse(List<OverpassElement> elements) {
}
