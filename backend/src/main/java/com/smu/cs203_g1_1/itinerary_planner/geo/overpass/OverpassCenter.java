package com.smu.cs203_g1_1.itinerary_planner.geo.overpass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Centre coordinates reported for a way or relation by Overpass {@code out center}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OverpassCenter(Double lat, Double lon) {
}
