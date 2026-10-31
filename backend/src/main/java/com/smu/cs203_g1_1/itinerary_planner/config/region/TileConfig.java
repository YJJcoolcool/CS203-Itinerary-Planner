package com.smu.cs203_g1_1.itinerary_planner.config.region;

/**
 * Basemap configuration for a region. The coordinate reference system travels
 * with the tile URL so a GCJ-02 basemap can never be mixed with WGS-84 pins.
 *
 * <p>{@code url} and {@code attribution} may be absent on the fallback entry;
 * {@code crs} is always required.</p>
 */
public record TileConfig(String url, String attribution, String crs) {
}
