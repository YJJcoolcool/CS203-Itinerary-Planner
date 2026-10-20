package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link PlaceScore}: one score per place and source. */
public class PlaceScoreId implements Serializable {

    private String locationId;

    private String source;

    public PlaceScoreId() {
    }

    public PlaceScoreId(String locationId, String source) {
        this.locationId = locationId;
        this.source = source;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PlaceScoreId that = (PlaceScoreId) o;
        return Objects.equals(locationId, that.locationId) && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, source);
    }
}
