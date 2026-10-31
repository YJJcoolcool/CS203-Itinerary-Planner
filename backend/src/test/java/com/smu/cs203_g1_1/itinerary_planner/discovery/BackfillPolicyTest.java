package com.smu.cs203_g1_1.itinerary_planner.discovery;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.smu.cs203_g1_1.itinerary_planner.testutil.MutableClock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackfillPolicyTest {

    private static final Instant START = Instant.parse("2026-11-02T10:00:00Z");

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START, ZoneOffset.UTC);
    }

    @Test
    void disabledFeatureNeverBackfills() {
        BackfillPolicy policy = new BackfillPolicy(clock, false, 100);

        assertFalse(policy.shouldBackfill(3.1390, 101.6869));
    }

    @Test
    void firstCallIsAllowedThenTheCellIsMemoisedForAnHour() {
        BackfillPolicy policy = new BackfillPolicy(clock, true, 100);

        assertTrue(policy.shouldBackfill(3.1390, 101.6869));
        policy.recordBackfill(3.1390, 101.6869);

        assertFalse(policy.shouldBackfill(3.1390, 101.6869));
    }

    @Test
    void memoExpiresAfterAnHour() {
        BackfillPolicy policy = new BackfillPolicy(clock, true, 100);
        policy.recordBackfill(3.1390, 101.6869);

        clock.advance(Duration.ofMinutes(61));

        assertTrue(policy.shouldBackfill(3.1390, 101.6869));
    }

    @Test
    void memoKeyUsesARoundedCellNotTheExactPoint() {
        BackfillPolicy policy = new BackfillPolicy(clock, true, 100);
        policy.recordBackfill(3.1390, 101.6869);

        assertFalse(policy.shouldBackfill(3.13902, 101.68692));
    }

    @Test
    void memoIsSizeCapped() {
        BackfillPolicy policy = new BackfillPolicy(clock, true, 2);

        policy.recordBackfill(1.0, 1.0);
        policy.recordBackfill(2.0, 2.0);
        policy.recordBackfill(3.0, 3.0);

        // The first entry was evicted by the size cap, so it is allowed again.
        assertTrue(policy.shouldBackfill(1.0, 1.0));
        assertFalse(policy.shouldBackfill(3.0, 3.0));
    }
}
