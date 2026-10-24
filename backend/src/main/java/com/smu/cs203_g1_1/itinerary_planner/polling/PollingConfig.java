package com.smu.cs203_g1_1.itinerary_planner.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler only when polling is explicitly switched on. Keeping
 * both the configuration and the poller behind the same property means the dev
 * boot and the test suite never start a background tick.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.polling", name = "enabled", havingValue = "true")
public class PollingConfig {
}
