package com.smu.cs203_g1_1.itinerary_planner.planner;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;

/**
 * One place that survived candidate filtering, together with the information
 * the suggestion layer needs.
 *
 * @param location     the seeded place
 * @param score        our deterministic ranking score
 * @param distanceKm   Haversine distance from the dropped stop in kilometres
 * @param openStatus   opening-hours evaluation at the requested arrival time
 * @param unknownHours true when the opening hours could not be parsed
 */
public record Candidate(Location location, double score, double distanceKm,
                        OpeningHours.Status openStatus, boolean unknownHours) {
}
