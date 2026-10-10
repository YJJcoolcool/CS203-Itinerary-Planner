package com.smu.cs203_g1_1.itinerary_planner.geo.dto;

/** Internal, API-facing representation of a geocoded place. */
public record PlaceDto(String osmType, Long osmId, String name, String displayName, String category, String type, double latitude, double longitude, AddressDto address, String openingHours, BoundingBoxDto boundingBox, Double importance) {

}

