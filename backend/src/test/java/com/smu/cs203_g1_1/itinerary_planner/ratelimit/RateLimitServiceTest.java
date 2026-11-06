package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimitService service = new RateLimitService(clock, 3, 5, 30);

    @Test
    void allowsUpToPerMinuteLimitThenTellsClientHowLongToWait() {
        consumeAllowed("alice", RateLimitTier.AI, 5);

        RateLimitDecision denied = service.tryConsume("alice", RateLimitTier.AI);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(12);
    }

    @Test
    void waitingForRetryAfterGrantsAToken() {
        consumeAllowed("alice", RateLimitTier.AI, 5);
        long wait = service.tryConsume("alice", RateLimitTier.AI).retryAfterSeconds();

        clock.advance(Duration.ofSeconds(wait));

        assertThat(service.tryConsume("alice", RateLimitTier.AI).allowed()).isTrue();
    }

    @Test
    void dailyLimitStillAppliesWhenRequestsArePacedUnderThePerMinuteLimit() {
        for (int i = 0; i < 30; i++) {
            assertThat(service.tryConsume("alice", RateLimitTier.AI).allowed()).isTrue();
            clock.advance(Duration.ofSeconds(12));
        }

        RateLimitDecision denied = service.tryConsume("alice", RateLimitTier.AI);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThan(60);
    }

    @Test
    void usersDoNotShareBuckets() {
        consumeAllowed("alice", RateLimitTier.AI, 5);

        assertThat(service.tryConsume("bob", RateLimitTier.AI).allowed()).isTrue();
    }

    @Test
    void tiersDoNotShareBuckets() {
        consumeAllowed("alice", RateLimitTier.AI, 5);

        assertThat(service.tryConsume("alice", RateLimitTier.GENERAL).allowed()).isTrue();
    }

    private void consumeAllowed(String userId, RateLimitTier tier, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(service.tryConsume(userId, tier).allowed()).isTrue();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
