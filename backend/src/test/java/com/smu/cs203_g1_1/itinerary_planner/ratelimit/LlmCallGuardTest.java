package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmCallGuardTest {

    private final Clock clock = mock(Clock.class);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T23:59:00Z"));
    }

    @Test
    void skipsCallsOnceTheDailyBudgetIsUsedAndResetsOnTheNextUtcDay() {
        LlmCallGuard guard = new LlmCallGuard(clock, 2, 4);
        assertThat(guard.call(() -> Optional.of("a"))).contains("a");
        assertThat(guard.call(() -> Optional.of("b"))).contains("b");

        assertThat(guard.call(() -> Optional.of("c"))).isEmpty();

        when(clock.instant()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(guard.call(() -> Optional.of("d"))).contains("d");
    }

    @Test
    void budgetOfZeroTurnsCallsOff() {
        LlmCallGuard guard = new LlmCallGuard(clock, 0, 4);

        assertThat(guard.call(() -> Optional.of("a"))).isEmpty();
    }

    @Test
    void skipsCallWhenAllSlotsAreBusyWithoutUsingBudget() {
        LlmCallGuard guard = new LlmCallGuard(clock, 2, 1);

        // The outer call holds the only slot while the inner one runs.
        Optional<Optional<String>> outer = guard.call(() -> Optional.of(guard.call(() -> Optional.of("inner"))));

        assertThat(outer).contains(Optional.empty());
        assertThat(guard.call(() -> Optional.of("second"))).contains("second");
        assertThat(guard.call(() -> Optional.of("third"))).isEmpty();
    }

    @Test
    void failedCallReleasesItsSlot() {
        LlmCallGuard guard = new LlmCallGuard(clock, 5, 1);

        assertThatThrownBy(() -> guard.call(() -> {
            throw new IllegalStateException("provider down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.call(() -> Optional.of("next"))).contains("next");
    }
}
