package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

/** Result of a rate limit check; {@code retryAfterSeconds} is 0 when allowed. */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
