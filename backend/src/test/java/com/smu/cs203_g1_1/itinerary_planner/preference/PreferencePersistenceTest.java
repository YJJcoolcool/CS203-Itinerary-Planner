package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PreferencePersistenceTest {

    @Autowired
    private UserPreferenceRepository users;

    @Autowired
    private TripPreferenceRepository trips;

    @Test
    void storesTagsAsCsvAndDefaultsSummaryToStale() {
        UserPreference preference = new UserPreference("user0001");
        preference.setTags(List.of("art", "food"));
        preference.setMaxWalkKm(new BigDecimal("1.50"));
        preference.setIndoorPreference("ANY");

        users.saveAndFlush(preference);

        UserPreference loaded = users.findById("user0001").orElseThrow();

        assertEquals(List.of("art", "food"), loaded.getTags());
        assertEquals(0, new BigDecimal("1.50").compareTo(loaded.getMaxWalkKm()));
        assertTrue(loaded.isSummaryStale());
    }

    @Test
    void nullTagsRoundTripAsAnEmptyList() {
        UserPreference preference = new UserPreference("user0002");
        preference.setTags(null);

        users.saveAndFlush(preference);

        assertTrue(users.findById("user0002").orElseThrow().getTags().isEmpty());
    }

    @Test
    void tripPreferenceTracksGoalAndStaleness() {
        TripPreference preference = new TripPreference("trip0001");
        preference.setGoals(List.of("MUSEUMS", "FOOD"));
        preference.setSummary("Likes museums and hawker food.");
        preference.setSummaryStale(false);

        trips.saveAndFlush(preference);

        TripPreference loaded = trips.findById("trip0001").orElseThrow();

        assertEquals(List.of("MUSEUMS", "FOOD"), loaded.getGoals());
        assertFalse(loaded.isSummaryStale());

        loaded.touch("user0001");
        trips.saveAndFlush(loaded);

        assertTrue(trips.findById("trip0001").orElseThrow().isSummaryStale());
    }
}
