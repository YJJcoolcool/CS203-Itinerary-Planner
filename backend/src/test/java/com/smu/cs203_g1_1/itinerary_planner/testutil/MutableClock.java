package com.smu.cs203_g1_1.itinerary_planner.testutil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Clock} whose instant can be moved by a test. Shared references keep
 * every bean that received the clock in sync.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.instant = new AtomicReference<>(Objects.requireNonNull(initialInstant, "initialInstant"));
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public void setInstant(Instant newInstant) {
        instant.set(Objects.requireNonNull(newInstant, "newInstant"));
    }

    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(Objects.requireNonNull(duration, "duration")));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        if (zone.equals(newZone)) {
            return this;
        }
        return new MutableClock(instant.get(), newZone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
