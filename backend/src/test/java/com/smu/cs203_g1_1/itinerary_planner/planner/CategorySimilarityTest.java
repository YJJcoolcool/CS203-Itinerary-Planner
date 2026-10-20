package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CategorySimilarityTest {

    private static final List<String> GROUPS = List.of(
            "culture", "nature", "attraction", "shopping", "food", "nightlife");

    @Test
    void diagonalIsOne() {
        for (String group : GROUPS) {
            assertEquals(1.0, CategorySimilarity.similarity(group, group), 1e-9);
        }
    }

    @Test
    void tableIsSymmetric() {
        for (String a : GROUPS) {
            for (String b : GROUPS) {
                assertEquals(CategorySimilarity.similarity(a, b),
                        CategorySimilarity.similarity(b, a), 1e-9,
                        a + " vs " + b);
            }
        }
    }

    @Test
    void unknownGroupScoresZero() {
        assertEquals(0.0, CategorySimilarity.similarity("culture", "adventure"), 1e-9);
        assertEquals(0.0, CategorySimilarity.similarity("adventure", "culture"), 1e-9);
        assertEquals(0.0, CategorySimilarity.similarity(null, "culture"), 1e-9);
        assertEquals(0.0, CategorySimilarity.similarity("culture", null), 1e-9);
        assertEquals(0.0, CategorySimilarity.similarity("  ", "culture"), 1e-9);
    }

    @Test
    void knownUnrelatedGroupsUseDefault() {
        // culture-nature is intentionally absent from the table.
        assertEquals(0.2, CategorySimilarity.similarity("culture", "nature"), 1e-9);
    }

    @Test
    void listedPairsUseTheTable() {
        assertEquals(0.5, CategorySimilarity.similarity("culture", "attraction"), 1e-9);
        assertEquals(0.6, CategorySimilarity.similarity("nature", "attraction"), 1e-9);
        assertEquals(0.1, CategorySimilarity.similarity("shopping", "nature"), 1e-9);
        assertEquals(0.4, CategorySimilarity.similarity("food", "shopping"), 1e-9);
        assertEquals(0.3, CategorySimilarity.similarity("culture", "nightlife"), 1e-9);
        assertEquals(0.5, CategorySimilarity.similarity("food", "nightlife"), 1e-9);
    }

    @Test
    void nightlifeIsAKnownGroup() {
        assertEquals(1.0, CategorySimilarity.similarity("nightlife", "nightlife"), 1e-9);
        assertTrue(CategorySimilarity.isKnownGroup("nightlife"));
    }

    @Test
    void isKnownGroupMatchesTheTableGroups() {
        for (String group : GROUPS) {
            assertTrue(CategorySimilarity.isKnownGroup(group));
        }
        assertFalse(CategorySimilarity.isKnownGroup("adventure"));
        assertFalse(CategorySimilarity.isKnownGroup(null));
    }
}
