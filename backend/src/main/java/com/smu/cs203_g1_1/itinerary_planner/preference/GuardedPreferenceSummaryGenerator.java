package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.smu.cs203_g1_1.itinerary_planner.ratelimit.LlmCallGuard;

/** The {@link PreferenceSummaryGenerator} callers get, so every LLM call passes through the guard. */
@Component
@Primary
public class GuardedPreferenceSummaryGenerator implements PreferenceSummaryGenerator {

    private final OpenAiPreferenceSummaryGenerator delegate;
    private final LlmCallGuard guard;

    public GuardedPreferenceSummaryGenerator(OpenAiPreferenceSummaryGenerator delegate, LlmCallGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public Optional<String> summariseGroup(List<UserPreference> members, TripPreference trip) {
        return guard.call(() -> delegate.summariseGroup(members, trip));
    }

    @Override
    public Optional<String> summariseUser(UserPreference preference, TravelHistory history) {
        return guard.call(() -> delegate.summariseUser(preference, history));
    }
}
