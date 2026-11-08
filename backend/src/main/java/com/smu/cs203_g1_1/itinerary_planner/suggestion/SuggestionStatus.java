package com.smu.cs203_g1_1.itinerary_planner.suggestion;

/** Lifecycle of a weather-driven alternative proposed for one activity. */
public enum SuggestionStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    /** Was accepted, then reversed by the user (CGT-169). */
    UNDONE
}
