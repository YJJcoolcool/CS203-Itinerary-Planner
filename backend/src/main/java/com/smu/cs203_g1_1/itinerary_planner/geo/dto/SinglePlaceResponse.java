package com.smu.cs203_g1_1.itinerary_planner.geo.dto;

/** Envelope for single-place geo responses: the result plus the OSM attribution text. */
public record SinglePlaceResponse(PlaceDto result, String attribution) {
}
