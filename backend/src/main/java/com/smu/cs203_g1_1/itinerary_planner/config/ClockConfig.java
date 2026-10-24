package com.smu.cs203_g1_1.itinerary_planner.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable time source so suggestion timestamps and the poller can
 * be driven by a fixed clock in tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Singapore"));
    }
}
