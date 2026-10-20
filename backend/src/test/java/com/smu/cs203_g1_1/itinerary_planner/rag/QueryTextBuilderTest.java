package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class QueryTextBuilderTest {

    @Test
    void includesGoalSummaryTagsAndConditions() {
        String text = QueryTextBuilder.build(
            "museums and hawker food",
            "quiet places, no long walks",
            Set.of("art", "food"),
            "rain expected in 2 hours");

        assertTrue(text.contains("Trip goal: museums and hawker food"));
        assertTrue(text.contains("Group preferences: quiet places, no long walks"));
        assertTrue(text.contains("Travellers like: art, food"));
        assertTrue(text.contains("Conditions: rain expected in 2 hours"));
    }

    @Test
    void sortsTagsSoTheCacheKeyIsStable() {
        String first = QueryTextBuilder.build(null, null, Set.of("zoo", "art", "food"), null);
        String second = QueryTextBuilder.build(null, null, Set.of("food", "zoo", "art"), null);

        assertEquals(first, second);
        assertTrue(first.contains("art, food, zoo"));
    }

    @Test
    void skipsBlankSections() {
        String text = QueryTextBuilder.build("  ", null, Set.of(), null);

        assertEquals("places to visit in Singapore", text);
        assertFalse(text.contains("Trip goal"));
    }
}
