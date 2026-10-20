package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PlanChoiceValidatorTest {

    private static final Set<String> IDS = Set.of("Cult0001", "Food0001");

    @Test
    void picksTheLowestRankedValidEntry() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Food0001", 2, 0.4, "Second best."),
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.")), false);

        Optional<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pick(choice, IDS);

        assertTrue(picked.isPresent());
        assertEquals("Cult0001", picked.get().id());
        assertEquals("Best match.", picked.get().reason());
    }

    @Test
    void dropsUnknownIds() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Nope0001", 1, 0.9, "Invented."),
                new PlanChoice.RankedChoice("Food0001", 2, 0.5, "Real.")), false);

        Optional<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pick(choice, IDS);

        assertTrue(picked.isPresent());
        assertEquals("Food0001", picked.get().id());
    }

    @Test
    void dropsDuplicateIds() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "First mention."),
                new PlanChoice.RankedChoice("Cult0001", 2, 0.8, "Duplicate.")), false);

        Optional<PlanChoice.RankedChoice> picked = PlanChoiceValidator.pick(choice, IDS);

        assertTrue(picked.isPresent());
        assertEquals("First mention.", picked.get().reason());
    }

    @Test
    void rejectsNoSuitableOption() {
        PlanChoice choice = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.")), true);

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
                        new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.")), false),
                null).isEmpty());
        assertTrue(PlanChoiceValidator.pick(
                new PlanChoice(List.of(
                        new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Best match.")), false),
                Set.of()).isEmpty());
    }
}
