package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Root of the curated Singapore POI seed file ({@code seed/sg_pois.json}).
 *
 * <p>The JSON uses snake_case keys; each record component carries an explicit
 * {@link JsonProperty} so the mapping does not depend on a global naming strategy.</p>
 */
public record SgPoiSeed(
        @JsonProperty("source") String source,
        @JsonProperty("extract_date") String extractDate,
        @JsonProperty("license") String license,
        @JsonProperty("attribution") String attribution,
        @JsonProperty("places") List<Place> places) {

    /**
     * One curated place. Fields that are optional in the file ({@code address},
     * {@code opening_hours}) are allowed to be null/blank; everything else is
     * checked by {@link PlaceSeeder} before anything is written.
     */
    public record Place(
            @JsonProperty("osm_type") String osmType,
            @JsonProperty("osm_id") Long osmId,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("category_group") String categoryGroup,
            @JsonProperty("latitude") Double latitude,
            @JsonProperty("longitude") Double longitude,
            @JsonProperty("indoor_class") String indoorClass,
            @JsonProperty("indoor_source") String indoorSource,
            @JsonProperty("opening_hours") String openingHours,
            @JsonProperty("address") String address,
            @JsonProperty("est_cost") BigDecimal estCost,
            @JsonProperty("quality_score") BigDecimal qualityScore,
            @JsonProperty("popularity_score") BigDecimal popularityScore,
            @JsonProperty("tags") Map<String, String> tags) {
    }
}
