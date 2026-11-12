package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @Column(length = 8, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 16)
    private ActivityType type = ActivityType.ACTIVITY;

    private String name;

    private ZonedDateTime startDateTime;

    private ZonedDateTime endDateTime;

    private String status;

    @Column(name = "flight_number", length = 20)
    private String flightNumber;

    @Column(name = "flight_date")
    private LocalDate flightDate;

    @Column(name = "departure_time_zone")
    private String departureTimeZone;

    @Column(name = "arrival_time_zone")
    private String arrivalTimeZone;

    @Column(length = 120)
    private String airline;

    @Column(name = "departure_airport_code", length = 8)
    private String departureAirportCode;

    @Column(name = "departure_airport_name", length = 160)
    private String departureAirportName;

    @Column(name = "departure_terminal", length = 32)
    private String departureTerminal;

    @Column(name = "arrival_airport_code", length = 8)
    private String arrivalAirportCode;

    @Column(name = "arrival_airport_name", length = 160)
    private String arrivalAirportName;

    @Column(name = "arrival_terminal", length = 32)
    private String arrivalTerminal;

    /*
     * Many activities may refer to one Location.
     *
     * Database:
     * activities.location_id -> locations.id
     */
    @ManyToMany
    @JoinTable(
            name = "activity_locations",
            joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id")
    )
    @OrderColumn(name = "location_order")
    private List<Location> locations = new ArrayList<>();

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


    public Activity( String name, List<Location> locations, ZonedDateTime startDateTime, ZonedDateTime endDateTime, String status) {
        this.name = name;
        this.locations = locations;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ActivityType getType() {
        return type;
    }

    public void setType(ActivityType type) {
        this.type = type == null ? ActivityType.ACTIVITY : type;
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

    public List<Location> getLocations() {
        return locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public LocalDate getFlightDate() {
        return flightDate;
    }

    public void setFlightDate(LocalDate flightDate) {
        this.flightDate = flightDate;
    }

    public String getDepartureTimeZone() {
        return departureTimeZone;
    }

    public void setDepartureTimeZone(String departureTimeZone) {
        this.departureTimeZone = departureTimeZone;
    }

    public String getArrivalTimeZone() {
        return arrivalTimeZone;
    }

    public void setArrivalTimeZone(String arrivalTimeZone) {
        this.arrivalTimeZone = arrivalTimeZone;
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public String getDepartureAirportCode() {
        return departureAirportCode;
    }

    public void setDepartureAirportCode(String code) {
        this.departureAirportCode = code;
    }

    public String getDepartureAirportName() {
        return departureAirportName;
    }

    public void setDepartureAirportName(String name) {
        this.departureAirportName = name;
    }

    public String getDepartureTerminal() {
        return departureTerminal;
    }

    public void setDepartureTerminal(String terminal) {
        this.departureTerminal = terminal;
    }

    public String getArrivalAirportCode() {
        return arrivalAirportCode;
    }

    public void setArrivalAirportCode(String code) {
        this.arrivalAirportCode = code;
    }

    public String getArrivalAirportName() {
        return arrivalAirportName;
    }

    public void setArrivalAirportName(String name) {
        this.arrivalAirportName = name;
    }

    public String getArrivalTerminal() {
        return arrivalTerminal;
    }

    public void setArrivalTerminal(String terminal) {
        this.arrivalTerminal = terminal;
    }
}