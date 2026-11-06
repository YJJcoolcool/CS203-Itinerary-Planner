package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PlanChoiceValidatorTest {

    private static final Set<String> IDS = Set.of("Cult0001", "Food0001", "Park0001");

    @Test
    void picksTheLowestRankedValidEntry() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Food0001", 2, 0.4, "Second best.", "Food hall."),
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.", "Museum.")), false);

        Optional<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pick(choice, IDS);

        assertTrue(picked.isPresent());
        assertEquals("Cult0001", picked.get().id());
        assertEquals("Best match.", picked.get().reason());
    }

    @Test
    void dropsUnknownIds() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Nope0001", 1, 0.9, "Invented.", "Invented."),
                new PlanChoice.RankedChoice("Food0001", 2, 0.5, "Real.", "Food hall.")), false);

        Optional<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pick(choice, IDS);

        assertTrue(picked.isPresent());
        assertEquals("Food0001", picked.get().id());
    }

    @Test
    void dropsDuplicateIds() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "First mention.", "Museum."),
                new PlanChoice.RankedChoice("Cult0001", 2, 0.8, "Duplicate.", "Museum.")), false);

        Optional<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pick(choice, IDS);

        assertTrue(picked.isPresent());
        assertEquals("First mention.", picked.get().reason());
    }

    @Test
    void rejectsNoSuitableOption() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.", "Museum.")), true);

        assertTrue(PlanChoiceValidator.pick(choice, IDS).isEmpty());
    }

    @Test
    void rejectsEmptyRanking() {
        assertTrue(PlanChoiceValidator.pick(new PlanChoice(List.of(), false), IDS).isEmpty());
        assertTrue(PlanChoiceValidator.pick(new PlanChoice(null, false), IDS).isEmpty());
    }

    @Test
    void rejectsNullInputs() {
        assertTrue(PlanChoiceValidator.pick(null, IDS).isEmpty());
        assertTrue(PlanChoiceValidator.pick(
                new PlanChoice(List.of(
                        new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.", "Museum.")), false),
                null).isEmpty());
        assertTrue(PlanChoiceValidator.pick(
                new PlanChoice(List.of(
                        new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.", "Museum.")), false),
                Set.of()).isEmpty());
    }

    @Test
    void pickAllReturnsUsableEntriesInRankOrder() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Food0001", 3, 0.3, "Third.", "Food hall."),
                new PlanChoice.RankedChoice("Park0001", 1, 0.9, "First.", "Park."),
                new PlanChoice.RankedChoice("Cult0001", 2, 0.6, "Second.", "Museum.")), false);

        List<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pickAll(choice, IDS, 5);

        assertEquals(List.of("Park0001", "Cult0001", "Food0001"),
                picked.stream().map(PlanChoice.RankedChoice::id).toList());
    }

    @Test
    void pickAllDropsInvalidAndDuplicateEntries() {
        PlanChoice choice = new PlanChoice(Arrays.asList(
                null,
                new PlanChoice.RankedChoice("Nope0001", 1, 0.9, "Unknown.", "Nope."),
                new PlanChoice.RankedChoice("Cult0001", 0, 0.9, "Bad rank.", "Museum."),
                new PlanChoice.RankedChoice("Food0001", 1, 0.9, " ", "Food hall."),
                new PlanChoice.RankedChoice("Park0001", 2, 0.7, "Good.", " "),
                new PlanChoice.RankedChoice("Park0001", 3, 0.5, "Duplicate.", "Park.")), false);

        List<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pickAll(choice, IDS, 5);

        assertEquals(1, picked.size());
        assertEquals("Park0001", picked.get(0).id());
        assertEquals("Good.", picked.get(0).reason());
    }

    @Test
    void pickAllHonoursTheLimit() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Food0001", 3, 0.3, "Third.", "Food hall."),
                new PlanChoice.RankedChoice("Park0001", 1, 0.9, "First.", "Park."),
                new PlanChoice.RankedChoice("Cult0001", 2, 0.6, "Second.", "Museum.")), false);

        List<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pickAll(choice, IDS, 2);

        assertEquals(2, picked.size());
        assertEquals(List.of("Park0001", "Cult0001"),
                picked.stream().map(PlanChoice.RankedChoice::id).toList());
    }

    @Test
    void pickAllRejectsEmptyInputsAndNonPositiveLimit() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.", "Museum.")), false);

        assertTrue(PlanChoiceValidator.pickAll(null, IDS, 5).isEmpty());
        assertTrue(PlanChoiceValidator.pickAll(new PlanChoice(List.of(), false), IDS, 5).isEmpty());
        assertTrue(PlanChoiceValidator.pickAll(choice, null, 5).isEmpty());
        assertTrue(PlanChoiceValidator.pickAll(choice, Set.of(), 5).isEmpty());
        assertTrue(PlanChoiceValidator.pickAll(choice, IDS, 0).isEmpty());
    }
}
