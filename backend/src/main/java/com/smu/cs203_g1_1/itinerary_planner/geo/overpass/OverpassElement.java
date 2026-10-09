package com.smu.cs203_g1_1.itinerary_planner.geo.overpass;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single Overpass element (node/way/relation) with optional coordinates and
 * tags.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OverpassElement(String type, Long id, Double lat, Double lon, OverpassCenter center, Map<String, String> tags) {

}
