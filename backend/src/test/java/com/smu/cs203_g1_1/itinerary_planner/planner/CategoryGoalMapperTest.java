package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CategoryGoalMapperTest {

    @Test
    void uppercaseGoalsMapToLowercaseGroups() {
        Set<String> groups = CategoryGoalMapper.toGroups(List.of(
                "MUSEUMS", "NATURE", "FOOD", "SHOPPING", "NIGHTLIFE", "ATTRACTIONS"));

        assertEquals(Set.of("culture", "nature", "food", "shopping",
                "nightlife", "attraction"), groups);
    }

    @Test
    void freeTextInterestChipsMap() {
        assertEquals(Set.of("culture"), CategoryGoalMapper.toGroups(List.of("art")));
        assertEquals(Set.of("culture"), CategoryGoalMapper.toGroups(List.of("history")));
        assertEquals(Set.of("nature"), CategoryGoalMapper.toGroups(List.of("photography")));
        assertEquals(Set.of("attraction"), CategoryGoalMapper.toGroups(List.of("family")));
    }

    @Test
    void inputsAreTrimmedAndCaseInsensitive() {
        assertEquals(Set.of("culture", "shopping"),
                CategoryGoalMapper.toGroups(List.of("  Art ", "ShOpPiNg")));
    }

    @Test
    void unknownAndBlankValuesAreIgnored() {
        assertEquals(Set.of("food"),
                CategoryGoalMapper.toGroups(List.of("food", "adventure", "", "   ")));
    }

    @Test
    void nullCollectionAndNullEntriesAreSafe() {
        assertTrue(CategoryGoalMapper.toGroups(null).isEmpty());

        List<String> values = Arrays.asList(null, "food", null);
        assertEquals(Set.of("food"), CategoryGoalMapper.toGroups(values));
    }
}
