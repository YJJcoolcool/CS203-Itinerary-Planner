package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.services.weather.NEAWeatherClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NeaWeatherSourceRegionTest {

    private static final ZonedDateTime AT = ZonedDateTime.of(
        2026, 11, 2, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private NEAWeatherClient client;

    private NeaWeatherSource source;

    @BeforeEach
    void setUp() {
        source = new NeaWeatherSource(client, Clock.fixed(AT.toInstant(), ZoneOffset.UTC),
            new RegionRegistry());
    }

    @Test
    void reportsItsProviderId() {
        assertEquals("nea", source.id());
    }

    @Test
    void coversSingaporeButNotKualaLumpur() {
        assertTrue(source.covers(1.3521, 103.8198));
        assertFalse(source.covers(3.1390, 101.6869));
    }

    @Test
    void rainyCheckOutsideSingaporeReturnsFalseWithoutCallingTheFeed() {
        assertFalse(source.isRainy(3.1390, 101.6869, AT, AT.plusHours(1)));

        verifyNoInteractions(client);
    }
}
