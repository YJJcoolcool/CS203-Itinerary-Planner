package com.smu.cs203_g1_1.itinerary_planner.polling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Real wiring: with polling enabled the poller bean exists. The long initial
 * delay keeps the fixed-delay tick out of the test.
 */
@SpringBootTest(properties = {
        "app.polling.enabled=true",
        "app.polling.initial-delay-ms=600000"})
class PollingContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void pollerIsWiredWhenPollingIsEnabled() {
        assertTrue(context.containsBean("weatherPoller"));
        assertNotNull(context.getBean(WeatherPoller.class));
    }
}
