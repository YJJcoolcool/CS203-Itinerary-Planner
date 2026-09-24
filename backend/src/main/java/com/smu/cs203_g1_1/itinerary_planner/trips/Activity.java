package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private ZonedDateTime startDateTime;

    private ZonedDateTime endDateTime;

    private String status;

    /*
     * Many activities may refer to one Location.
     *
     * Database:
     * activities.location_id -> locations.id
     */
    @ManyToOne
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /*
     * Many activities belong to one Trip.
     *
     * Database:
     * activities.trip_id -> trips.id
     */
    @ManyToOne
    @JoinColumn(name = "trip_id", nullable = false)
    @JsonIgnore
    private Trip trip;


    // Required by JPA
    public Activity() {
    }


    public Activity( String name, Location location, ZonedDateTime startDateTime, ZonedDateTime endDateTime, String status) {

        this.name = name;
        this.location = location;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = status;
    }


    public Long getId() {
        return id;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    public ZonedDateTime getStartDateTime() {
        return startDateTime;
    }


    public void setStartDateTime(ZonedDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }


    public ZonedDateTime getEndDateTime() {
        return endDateTime;
    }


    public void setEndDateTime(ZonedDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public Location getLocation() {
        return location;
    }


    public void setLocation(Location location) {
        this.location = location;
    }


    public Trip getTrip() {
        return trip;
    }


    public void setTrip(Trip trip) {
        this.trip = trip;
    }
}