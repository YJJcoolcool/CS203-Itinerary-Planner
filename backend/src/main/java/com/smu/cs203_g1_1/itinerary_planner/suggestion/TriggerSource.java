package com.smu.cs203_g1_1.itinerary_planner.suggestion;

/**
 * Creation horizon: a user request, the tomorrow-day-ahead poll, or the
 * imminent-start poll. The value describes where the suggestion came from,
 * not which scheduler tick produced it.
 */
public enum TriggerSource {
    MANUAL,
    POLL_TOMORROW,
    POLL_IMMINENT
}
