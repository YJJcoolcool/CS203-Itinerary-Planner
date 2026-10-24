package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.Instant;

import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A pending/decided swap for one stop of an itinerary.
 *
 * <p>The suggestion pins the activity, the location currently on it that the
 * weather problem affects, and the alternative the deterministic shortlist
 * picked. Accepting replaces that one location; rejecting only records the
 * decision.</p>
 */
@Entity
@Table(name = "suggestions")
public class Suggestion {

    @Id
    @Column(length = 8, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /** The location currently on the activity that the suggestion would replace. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_location_id")
    private Location currentLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_location_id", nullable = false)
    private Location proposedLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(length = 255)
    private String reason;

    @Column(length = 1024)
    private String explanation;

    /** Shortlist snapshot: an array of {id, name, score, distanceKm}. */
    @Column(name = "candidates", length = 4096)
    private String candidatesJson;

    /** The candidate the model picked, when the AI chooser ran; null for the fallback. */
    @Column(name = "ai_choice_id", length = 8)
    private String aiChoiceId;

    /** The candidate our deterministic ranking picked. */
    @Column(name = "top_score_choice_id", length = 8)
    private String topScoreChoiceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** Whether a user asked for this suggestion or a poller created it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 16)
    private TriggerSource triggerSource = TriggerSource.MANUAL;

    // Required by JPA.
    public Suggestion() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public Location getProposedLocation() {
        return proposedLocation;
    }

    public void setProposedLocation(Location proposedLocation) {
        this.proposedLocation = proposedLocation;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public void setStatus(SuggestionStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCandidatesJson() {
        return candidatesJson;
    }

    public void setCandidatesJson(String candidatesJson) {
        this.candidatesJson = candidatesJson;
    }

    public String getAiChoiceId() {
        return aiChoiceId;
    }

    public void setAiChoiceId(String aiChoiceId) {
        this.aiChoiceId = aiChoiceId;
    }

    public String getTopScoreChoiceId() {
        return topScoreChoiceId;
    }

    public void setTopScoreChoiceId(String topScoreChoiceId) {
        this.topScoreChoiceId = topScoreChoiceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(TriggerSource triggerSource) {
        this.triggerSource = triggerSource;
    }
}
