package com.smu.cs203_g1_1.itinerary_planner.geo.dto;

/** Structured address components of a place. */
public record AddressDto(String houseNumber, String road, String city, String state, String postcode, String country, String countryCode) {
}
