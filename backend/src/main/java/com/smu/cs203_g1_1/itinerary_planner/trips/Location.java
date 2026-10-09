package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.ArrayList;
import java.util.List;

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
@Table(
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_location_osm",
            columnNames = {"osm_type", "osm_id"}
        )
    }
)
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

    @Column(name = "osm_type")
    private String osmType;

    @Column(name = "osm_id")
    private Long osmId;


    // Not sure if want to include
    /*
    private String address; this one kinda obv
    private String openingHours (for extra context for AI)
    */

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

    /*    
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getOsmId() {
        return osmId;
    }

    public void setosmId(String osmId) {
        this.osmId = osmId;
    }
    
    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }
    */

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
