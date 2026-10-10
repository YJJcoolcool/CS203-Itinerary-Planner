package com.smu.cs203_g1_1.itinerary_planner.geo.nominatim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw Nominatim address block. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimAddress(
        @JsonProperty("house_number") String houseNumber,
        @JsonProperty("road") String road,
        @JsonProperty("city") String city,
        @JsonProperty("town") String town,
        @JsonProperty("village") String village,
        @JsonProperty("state") String state,
        @JsonProperty("postcode") String postcode,
        @JsonProperty("country") String country,
        @JsonProperty("country_code") String countryCode) {
}
