package com.smu.cs203_g1_1.itinerary_planner.trips;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private Double latitude;
    private Double longitude;
    private Boolean isIndoor;

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
    }

    // Getters/Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
}
