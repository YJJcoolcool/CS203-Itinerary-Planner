package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** App-wide cap on LLM calls, covering users and the poller. Returns empty so callers use their fallback. */
@Component
public class LlmCallGuard {

    private static final Logger log = LoggerFactory.getLogger(LlmCallGuard.class);

    private final Clock clock;
    private final long dailyCallBudget;
    private final Semaphore callSlots;
    private Instant budgetDay;
    private long callsUsed;

    public LlmCallGuard(Clock clock,
                        @Value("${app.llm.daily-call-budget}") long dailyCallBudget,
                        @Value("${app.llm.max-concurrent-calls}") int maxConcurrentCalls) {
        this.clock = clock;
        this.dailyCallBudget = dailyCallBudget;
        this.callSlots = new Semaphore(maxConcurrentCalls);
    }

    public <T> Optional<T> call(Supplier<Optional<T>> llmCall) {
        // Never wait for a slot: callers are inside a transaction, so queueing would hold DB connections.
        if (!callSlots.tryAcquire()) {
            log.warn("LLM call skipped: concurrency limit reached");
            return Optional.empty();
        }
        try {
            if (!tryUseBudget()) {
                log.warn("LLM call skipped: daily budget of {} used", dailyCallBudget);
                return Optional.empty();
            }
            return llmCall.get();
        } finally {
            callSlots.release();
        }
    }

    // A strict count per UTC day, because a refilling bucket could exceed the budget.
    private synchronized boolean tryUseBudget() {
        Instant today = clock.instant().truncatedTo(ChronoUnit.DAYS);
        if (!today.equals(budgetDay)) {
            budgetDay = today;
            callsUsed = 0;
        }
        if (callsUsed >= dailyCallBudget) {
            return false;
        }
        callsUsed++;
        return true;
    }
}
