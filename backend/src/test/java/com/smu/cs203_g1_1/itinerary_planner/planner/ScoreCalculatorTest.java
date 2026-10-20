package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    void sameGroupBeatsUnrelatedGroupAllElseEqual() {
        double sameGroup = calculator.score("culture", "culture", Set.of(),
                1.0, new BigDecimal("10"), false, 0.5);
        double unrelated = calculator.score("food", "culture", Set.of(),
                1.0, new BigDecimal("10"), false, 0.5);

        assertTrue(sameGroup > unrelated);
    }

    @Test
    void matchingInterestBoostsScoreByPreferenceWeight() {
        double withoutInterest = calculator.score("culture", "attraction", Set.of("nature"),
                1.0, new BigDecimal("10"), false, 0.5);
        double withInterest = calculator.score("culture", "attraction", Set.of("culture"),
                1.0, new BigDecimal("10"), false, 0.5);

        assertEquals(ScoreCalculator.PREFERENCE_WEIGHT, withInterest - withoutInterest, 1e-9);
    }

    @Test
    void distancePenaltyIncreasesWithKmAndCapsAtOne() {
        assertEquals(0.0, ScoreCalculator.distancePenalty(0.0), 1e-9);
        assertEquals(0.5, ScoreCalculator.distancePenalty(1.5), 1e-9);
        assertEquals(1.0, ScoreCalculator.distancePenalty(3.0), 1e-9);
        assertEquals(1.0, ScoreCalculator.distancePenalty(50.0), 1e-9);
        assertEquals(0.0, ScoreCalculator.distancePenalty(-4.0), 1e-9);

        assertTrue(ScoreCalculator.distancePenalty(1.0) < ScoreCalculator.distancePenalty(2.0));
    }

    @Test
    void costPenaltyCapsAtMaximum() {
        assertEquals(0.0, ScoreCalculator.costPenalty(null), 1e-9);
        assertEquals(0.0, ScoreCalculator.costPenalty(BigDecimal.ZERO), 1e-9);
        assertEquals(0.1, ScoreCalculator.costPenalty(new BigDecimal("10")), 1e-9);
        assertEquals(0.3, ScoreCalculator.costPenalty(new BigDecimal("30")), 1e-9);
        assertEquals(0.3, ScoreCalculator.costPenalty(new BigDecimal("1000")), 1e-9);
        assertEquals(0.0, ScoreCalculator.costPenalty(new BigDecimal("-10")), 1e-9);
    }

    @Test
    void unknownHoursAppliesExactlyOneQuarter() {
        double knownHours = calculator.score("culture", "culture", Set.of(),
                1.0, new BigDecimal("10"), false, 0.5);
        double unknownHours = calculator.score("culture", "culture", Set.of(),
                1.0, new BigDecimal("10"), true, 0.5);

        assertEquals(0.25, knownHours - unknownHours, 1e-9);
        assertEquals(ScoreCalculator.UNKNOWN_HOURS_PENALTY, knownHours - unknownHours, 1e-9);
    }

    @Test
    void identicalInputsAreDeterministic() {
        double first = calculator.score("nature", "attraction", Set.of("nature"),
                2.4, new BigDecimal("12.50"), true, 0.75);
        double second = calculator.score("nature", "attraction", Set.of("nature"),
                2.4, new BigDecimal("12.50"), true, 0.75);

        assertEquals(first, second, 0.0);
    }

    @Test
    void nullCostCountsAsZeroAndNeverProducesNaN() {
        double nullCost = calculator.score("culture", "culture", Set.of(),
                1.0, null, false, 0.5);
        double zeroCost = calculator.score("culture", "culture", Set.of(),
                1.0, BigDecimal.ZERO, false, 0.5);

        assertFalse(Double.isNaN(nullCost));
        assertEquals(zeroCost, nullCost, 1e-9);
    }

    @Test
    void qualityIsClampedToZeroOne() {
        double aboveOne = calculator.score("culture", "culture", Set.of(),
                1.0, BigDecimal.ZERO, false, 2.0);
        double atOne = calculator.score("culture", "culture", Set.of(),
                1.0, BigDecimal.ZERO, false, 1.0);
        double belowZero = calculator.score("culture", "culture", Set.of(),
                1.0, BigDecimal.ZERO, false, -1.0);
        double atZero = calculator.score("culture", "culture", Set.of(),
                1.0, BigDecimal.ZERO, false, 0.0);

        assertEquals(atOne, aboveOne, 1e-9);
        assertEquals(atZero, belowZero, 1e-9);
    }

    @Test
    void fullFormulaMatchesConstants() {
        double actual = calculator.score("nature", "attraction", Set.of("nature"),
                1.5, new BigDecimal("20"), true, 0.5);

        double expected = ScoreCalculator.SIMILARITY_WEIGHT * 0.6
                + ScoreCalculator.PREFERENCE_WEIGHT * 1.0
                + ScoreCalculator.QUALITY_WEIGHT * 0.5
                - 0.5
                - 0.2
                - ScoreCalculator.UNKNOWN_HOURS_PENALTY;

        assertEquals(expected, actual, 1e-9);
    }
}
