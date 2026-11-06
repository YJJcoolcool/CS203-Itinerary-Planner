package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

/** Per-user, per-tier token buckets, held in memory because we run a single backend instance. */
@Service
public class RateLimitService {

    private static final long MAX_TRACKED_BUCKETS = 20_000;
    // Must outlast the longest refill window, or evicting a bucket would hand back spent tokens.
    private static final Duration IDLE_EXPIRY = Duration.ofDays(2);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_BUCKETS)
            .expireAfterAccess(IDLE_EXPIRY)
            .build();
    private final Map<RateLimitTier, List<Bandwidth>> limits;
    private final TimeMeter timeMeter;

    public RateLimitService(Clock clock,
                            @Value("${app.rate-limit.general.per-minute}") long generalPerMinute,
                            @Value("${app.rate-limit.ai.per-minute}") long aiPerMinute,
                            @Value("${app.rate-limit.ai.per-day}") long aiPerDay) {
        this.timeMeter = new ClockTimeMeter(clock);
        this.limits = Map.of(
                RateLimitTier.GENERAL, List.of(limit(generalPerMinute, Duration.ofMinutes(1))),
                RateLimitTier.AI, List.of(
                        limit(aiPerMinute, Duration.ofMinutes(1)),
                        limit(aiPerDay, Duration.ofDays(1))));
    }

    /** Takes one token from the user's bucket for this tier. */
    public RateLimitDecision tryConsume(String userId, RateLimitTier tier) {
        Bucket bucket = buckets.get(tier + ":" + userId, key -> newBucket(tier));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new RateLimitDecision(true, 0);
        }
        return new RateLimitDecision(false, toRetryAfterSeconds(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket(RateLimitTier tier) {
        var builder = Bucket.builder().withCustomTimePrecision(timeMeter);
        for (Bandwidth bandwidth : limits.get(tier)) {
            builder.addLimit(bandwidth);
        }
        return builder.build();
    }

    private static Bandwidth limit(long tokens, Duration window) {
        return Bandwidth.builder().capacity(tokens).refillGreedy(tokens, window).build();
    }

    // Rounds up so a client that waits this long has waited enough.
    private static long toRetryAfterSeconds(long nanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos + 999_999_999L));
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            return TimeUnit.MILLISECONDS.toNanos(clock.millis());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
