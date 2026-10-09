package com.smu.cs203_g1_1.itinerary_planner.geo.dto;

/** Geographic bounding box expressed as min/max latitude and longitude. */
public record BoundingBoxDto(double minLat, double minLon, double maxLat, double maxLon) {
}
