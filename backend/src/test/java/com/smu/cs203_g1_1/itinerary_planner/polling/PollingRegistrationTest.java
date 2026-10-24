package com.smu.cs203_g1_1.itinerary_planner.polling;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionService;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;

class PollingRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PollingConfig.class, WeatherPoller.class)
            .withBean(TripRepository.class, () -> mock(TripRepository.class))
            .withBean(ActivityRepository.class, () -> mock(ActivityRepository.class))
            .withBean(SuggestionService.class, () -> mock(SuggestionService.class))
            .withBean(WeatherSource.class, () -> mock(WeatherSource.class))
            .withBean(Clock.class, () -> Clock.fixed(
                    Instant.parse("2026-11-02T07:00:00Z"), ZoneId.of("Asia/Singapore")));

    @Test
    void pollerIsNotRegisteredWhenThePropertyIsAbsent() {
        runner.run(context -> {
            assertFalse(context.containsBean("weatherPoller"));
            assertEquals(0, context.getBeansOfType(WeatherPoller.class).size());
            assertEquals(0, context.getBeansOfType(PollingConfig.class).size());
        });
    }

    @Test
    void pollerIsRegisteredWhenThePropertyIsTrue() {
        runner.withPropertyValues("app.polling.enabled=true")
                .run(context -> {
                    assertTrue(context.containsBean("weatherPoller"));
                    assertEquals(1, context.getBeansOfType(WeatherPoller.class).size());
                    assertEquals(1, context.getBeansOfType(PollingConfig.class).size());
                });
    }
}
