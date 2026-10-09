package com.smu.cs203_g1_1.itinerary_planner.geo.nominatim;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw Nominatim place record as returned by the API. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimPlace(
        @JsonProperty("osm_type") String osmType,
        @JsonProperty("osm_id") Long osmId,
        @JsonProperty("lat") String lat,
        @JsonProperty("lon") String lon,
        @JsonProperty("category") String category,
        @JsonProperty("type") String type,
        @JsonProperty("name") String name,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("importance") Double importance,
        @JsonProperty("boundingbox") List<String> boundingbox,
        @JsonProperty("address") NominatimAddress address,
        @JsonProperty("extratags") Map<String, String> extratags) {
}
