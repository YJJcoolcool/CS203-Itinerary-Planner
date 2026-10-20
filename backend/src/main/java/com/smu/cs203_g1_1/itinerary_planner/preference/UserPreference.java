package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One user's stated travel preferences.
 *
 * <p>Readable by other signed-in users (structured fields only), so it must not
 * hold anything private beyond what the user chose to publish. {@code summary}
 * is the AI's rendering of these preferences; {@code summaryStale} marks it as
 * needing regeneration after an edit.</p>
 */
@Entity
@Table(name = "user_preference")
public class UserPreference {

    @Id
    @Column(name = "user_id", length = 8, nullable = false)
    private String userId;

    @Convert(converter = CsvListConverter.class)
    @Column(length = 255)
    private List<String> tags = new ArrayList<>();

    @Column(name = "max_walk_km", precision = 4, scale = 2)
    private BigDecimal maxWalkKm;

    @Column(length = 64)
    private String dietary;

    /** INDOOR, OUTDOOR or ANY; null means no preference. */
    @Column(name = "indoor_preference", length = 16)
    private String indoorPreference;

    @Column(length = 1024)
    private String notes;

    @Column(length = 1024)
    private String summary;

    @Column(name = "summary_stale", nullable = false)
    private boolean summaryStale = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreference() {
        // JPA
    }

    public UserPreference(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getTags() {
        return tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public BigDecimal getMaxWalkKm() {
        return maxWalkKm;
    }

    public void setMaxWalkKm(BigDecimal maxWalkKm) {
        this.maxWalkKm = maxWalkKm;
    }

    public String getDietary() {
        return dietary;
    }

    public void setDietary(String dietary) {
        this.dietary = dietary;
    }

    public String getIndoorPreference() {
        return indoorPreference;
    }

    public void setIndoorPreference(String indoorPreference) {
        this.indoorPreference = indoorPreference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
