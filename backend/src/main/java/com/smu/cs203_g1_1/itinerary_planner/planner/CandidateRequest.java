package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Input to {@link CandidateFinder#find(CandidateRequest)}.
 *
 * @param droppedLocationId    id of the stop being replaced; must be non-blank
 * @param arrival              expected arrival time at the replacement; must be non-null
 * @param requireIndoor        when true, keep only {@code INDOOR} places
 * @param interests            user interest groups; may be null
 * @param limit                maximum number of candidates, 1..50
 * @param excludedLocationIds  ids already in the itinerary; may be null
 */
public record CandidateRequest(String droppedLocationId, ZonedDateTime arrival,
                               boolean requireIndoor, Set<String> interests,
                               int limit, Set<String> excludedLocationIds) {
}
