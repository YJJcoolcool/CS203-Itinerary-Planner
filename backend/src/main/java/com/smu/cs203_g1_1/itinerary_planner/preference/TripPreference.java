package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A trip's group preference: the AI summary of every member's preference plus
 * the trip's own goal.
 *
 * <p>Owned by the trip, not by a user: anyone in the group sees the same
 * summary. {@code goals} is the trip's category selection (museums, food, ...),
 * which also feeds retrieval.</p>
 */
@Entity
@Table(name = "trip_preference")
public class TripPreference {

    @Id
    @Column(name = "trip_id", length = 8, nullable = false)
    private String tripId;

    @Convert(converter = CsvListConverter.class)
    @Column(length = 255)
    private List<String> goals = new ArrayList<>();

    @Convert(converter = CsvListConverter.class)
    @Column(length = 255)
    private List<String> tags = new ArrayList<>();

    @Column(length = 1024)
    private String summary;

    @Column(name = "summary_stale", nullable = false)
    private boolean summaryStale = true;

    @Column(name = "updated_by", length = 8)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TripPreference() {
        // JPA
    }

    public TripPreference(String tripId) {
        this.tripId = tripId;
    }

    public String getTripId() {
        return tripId;
    }

    public List<String> getGoals() {
        return goals == null ? new ArrayList<>() : new ArrayList<>(goals);
    }

    public void setGoals(List<String> goals) {
        this.goals = goals == null ? new ArrayList<>() : new ArrayList<>(goals);
    }

    public List<String> getTags() {
        return tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isSummaryStale() {
        return summaryStale;
    }

    public void setSummaryStale(boolean summaryStale) {
        this.summaryStale = summaryStale;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(String userId) {
        this.updatedBy = userId;
        this.updatedAt = Instant.now();
        this.summaryStale = true;
    }
}
