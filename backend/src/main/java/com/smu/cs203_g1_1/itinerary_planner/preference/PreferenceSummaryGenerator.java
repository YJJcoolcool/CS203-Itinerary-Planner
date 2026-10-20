package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.util.List;
import java.util.Optional;

/**
 * Turns structured preferences into a short natural-language summary.
 *
 * <p>Always optional: callers fall back to a deterministic text when no model is
 * configured or the call fails. A summary is presentation, never a constraint.</p>
 */
public interface PreferenceSummaryGenerator {

    Optional<String> summariseGroup(List<UserPreference> members, TripPreference trip);

    Optional<String> summariseUser(UserPreference preference);
}
