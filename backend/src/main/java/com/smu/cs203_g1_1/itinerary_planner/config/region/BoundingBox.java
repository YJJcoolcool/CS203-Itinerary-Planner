package com.smu.cs203_g1_1.itinerary_planner.config.region;

/**
 * Inclusive latitude/longitude bounding box used for region coverage lookup.
 */
public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {

    public BoundingBox {
        validate();
    }

    /**
     * @return {@code true} when the point lies inside the box, boundary included
     */
    public boolean contains(double latitude, double longitude) {
        return latitude >= minLat && latitude <= maxLat
            && longitude >= minLon && longitude <= maxLon;
    }

    /**
     * Rejects inverted ranges and coordinates outside the legal lat/lon domain.
     *
     * @throws IllegalArgumentException when the box is not a valid WGS-84 range
     */
    public void validate() {
        if (!Double.isFinite(minLat) || !Double.isFinite(maxLat)
            || !Double.isFinite(minLon) || !Double.isFinite(maxLon)) {
            throw new IllegalArgumentException("Bounding box coordinates must be finite");
        }
        if (minLat > maxLat) {
            throw new IllegalArgumentException(
                "Bounding box latitude range is inverted: " + minLat + " > " + maxLat);
        }
        if (minLon > maxLon) {
            throw new IllegalArgumentException(
                "Bounding box longitude range is inverted: " + minLon + " > " + maxLon);
        }
        if (minLat < -90.0 || maxLat > 90.0) {
            throw new IllegalArgumentException(
                "Bounding box latitude is outside [-90, 90]: "
                    + minLat + ".." + maxLat);
        }
        if (minLon < -180.0 || maxLon > 180.0) {
            throw new IllegalArgumentException(
                "Bounding box longitude is outside [-180, 180]: "
                    + minLon + ".." + maxLon);
        }
    }
}
