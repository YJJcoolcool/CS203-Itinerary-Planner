package com.smu.cs203_g1_1.itinerary_planner.trips;

/**
 * Body of {@code POST /api/trips/{tripId}/locations}: a place the user types in.
 *
 * <p>Only these fields are accepted from the client; everything else on
 * {@link Location} (seeded, OSM identity, owning trip) is set by the server.</p>
 *
 * @param indoorClass {@code INDOOR}, {@code OUTDOOR}, or null when the user is not sure (CGT-141)
 */
public record ManualLocationRequest(String name,
                                    String category,
                                    Double latitude,
                                    Double longitude,
                                    String indoorClass) {
}
