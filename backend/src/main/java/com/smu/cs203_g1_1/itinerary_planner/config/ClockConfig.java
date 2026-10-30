package com.smu.cs203_g1_1.itinerary_planner.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable time source so suggestion timestamps and the poller can
 * be driven by a fixed clock in tests.
 *
 * <p>The bean is deliberately zone-free: it yields {@link java.time.Instant}
 * values and no zone is baked into the global clock. Zone-dependent
 * derivations ({@code LocalDate.now(clock)}, {@code atStartOfDay}) must resolve
 * a {@link java.time.ZoneId} from the trip's region via
 * {@code RegionRegistry} instead of assuming Singapore.</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
