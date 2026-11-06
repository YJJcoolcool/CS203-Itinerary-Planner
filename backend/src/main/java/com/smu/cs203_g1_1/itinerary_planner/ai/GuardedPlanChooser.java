package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.smu.cs203_g1_1.itinerary_planner.ratelimit.LlmCallGuard;

/** The {@link PlanChooser} callers get, so every LLM call passes through the guard. */
@Component
@Primary
public class GuardedPlanChooser implements PlanChooser {

    private final OpenAiPlanChooser delegate;
    private final LlmCallGuard guard;

    public GuardedPlanChooser(OpenAiPlanChooser delegate, LlmCallGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public Optional<PlanChoice> choose(PlanChoiceRequest request) {
        return guard.call(() -> delegate.choose(request));
    }
}
