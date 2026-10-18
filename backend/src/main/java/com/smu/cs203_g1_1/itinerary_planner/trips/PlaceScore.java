package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Our own score for a seeded place, kept separately from {@link Location} so its source and age
 * are visible and it can be recomputed without touching the place record.
 *
 * <p>There is deliberately no JPA association to {@link Location}: the database owns the
 * {@code ON DELETE CASCADE}, and the scorer only needs the id.</p>
 */
@Entity
@Table(name = "place_scores")
@IdClass(PlaceScoreId.class)
public class PlaceScore {

    @Id
    @Column(name = "location_id", length = 8, nullable = false)
    private String locationId;

    /** MANUAL or RULE. */
    @Id
    @Column(length = 16, nullable = false)
    private String source;

    /** Curated notability/quality, 0..1. */
    @Column(name = "quality_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal qualityScore;

    /** Wikidata/manual popularity proxy, 0..1. */
    @Column(name = "popularity_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal popularityScore;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    // Required by JPA
    public PlaceScore() {
    }

    public PlaceScore(String locationId, String source, BigDecimal qualityScore,
                      BigDecimal popularityScore, Instant scoredAt) {
        this.locationId = locationId;
        this.source = source;
        this.qualityScore = qualityScore;
        this.popularityScore = popularityScore;
        this.scoredAt = scoredAt;
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

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public BigDecimal getPopularityScore() {
        return popularityScore;
    }

    public void setPopularityScore(BigDecimal popularityScore) {
        this.popularityScore = popularityScore;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(Instant scoredAt) {
        this.scoredAt = scoredAt;
    }
}
