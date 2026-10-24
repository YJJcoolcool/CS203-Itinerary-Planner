package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;


@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uq_location_osm_identity", columnNames = {"osm_type", "osm_id"}))
public class Location {

    @Id
    @Column(length = 8, nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Boolean isIndoor;

    /** OSM element type ("node"/"way"/"relation"); part of the OSM identity. */
    @Column(name = "osm_type")
    private String osmType;

    /** OSM element id; together with {@link #osmType} uniquely identifies the place. */
    @Column(name = "osm_id")
    private Long osmId;

    /** Human-readable address / display name from OSM. */
    @Column(name = "address")
    private String address;

    /** Free-form OSM opening_hours value, when known. */
    @Column(name = "opening_hours")
    private String openingHours;

    /** Coarse interest group used for candidate compatibility (e.g. culture, nature, food). */
    @Column(name = "category_group", length = 32)
    private String categoryGroup;

    /** INDOOR, OUTDOOR, or UNKNOWN; null when never classified. */
    @Column(name = "indoor_class", length = 16)
    private String indoorClass;

    /** Where {@link #indoorClass} came from: OSM_TAG, MANUAL, or AI. */
    @Column(name = "indoor_source", length = 16)
    private String indoorSource;

    /** Classifier confidence in {@link #indoorClass}, 0..1. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "indoor_confidence", precision = 3, scale = 2)
    private Double indoorConfidence;

    /** Estimated entry/visit cost, in local currency. */
    @Column(name = "est_cost", precision = 10, scale = 2)
    private BigDecimal estCost;

    /** Parsed opening intervals, stored as a compact string (never jsonb; H2 tests build from the entity). */
    @Column(name = "open_intervals", length = 2048)
    private String openIntervals;

    /** True when the row came from the curated seed set. */
    @Column(name = "seeded", nullable = false)
    private boolean seeded = false;

    @ManyToOne
    @JoinColumn(name = "parent_location_id")
    private Location parentLocation;

    @OneToMany(mappedBy = "parentLocation") 
    private List<Location> subLocations = new ArrayList<>();

    @ManyToMany(mappedBy = "locations")
    @JsonIgnore
    private List<Activity> activities = new ArrayList<>();

    // No arg constructor
    public Location() {

    }

    // Constructor 
    public Location(String name, String category, Double latitude, Double longitude, Boolean isIndoor) {
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isIndoor = isIndoor;
        /*
        this.address = address;
        this.osmId = osmId;
        this.openingHours = openingHours;
        */
    }

    // Getters/Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getIsIndoor() {
        return isIndoor;
    }

    public void setIsIndoor(Boolean isIndoor) {
        this.isIndoor = isIndoor;
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }

    public String getOsmType() {
        return osmType;
    }

    public void setOsmType(String osmType) {
        this.osmType = osmType;
    }

    public Long getOsmId() {
        return osmId;
    }

    public void setOsmId(Long osmId) {
        this.osmId = osmId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public String getCategoryGroup() {
        return categoryGroup;
    }

    public void setCategoryGroup(String categoryGroup) {
        this.categoryGroup = categoryGroup;
    }

    public String getIndoorClass() {
        return indoorClass;
    }

    public void setIndoorClass(String indoorClass) {
        this.indoorClass = indoorClass;
    }

    /** True when the classifier (or a human) marked this place as an outdoor stop. */
    public boolean isOutdoor() {
        return "OUTDOOR".equalsIgnoreCase(indoorClass);
    }

    public String getIndoorSource() {
        return indoorSource;
    }

    public void setIndoorSource(String indoorSource) {
        this.indoorSource = indoorSource;
    }

    public Double getIndoorConfidence() {
        return indoorConfidence;
    }

    public void setIndoorConfidence(Double indoorConfidence) {
        this.indoorConfidence = indoorConfidence;
    }

    public BigDecimal getEstCost() {
        return estCost;
    }

    public void setEstCost(BigDecimal estCost) {
        this.estCost = estCost;
    }

    public String getOpenIntervals() {
        return openIntervals;
    }

    public void setOpenIntervals(String openIntervals) {
        this.openIntervals = openIntervals;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }

    // Utility methods
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Location location = (Location) o;
        return id != null && id.equals(location.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "[Location:" + " id = " + id + ", name = " + name + ", category = " + category + ", isIndoor = " + isIndoor + "]";
    }
}
