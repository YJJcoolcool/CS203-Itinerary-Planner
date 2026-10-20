package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class OpeningHoursTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    // 2026-11-02 is a Monday.
    private static final ZonedDateTime MON_0100 = ZonedDateTime.of(2026, 11, 2, 1, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_0300 = ZonedDateTime.of(2026, 11, 2, 3, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_0900 = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_1000 = ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_1200 = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_1300 = ZonedDateTime.of(2026, 11, 2, 13, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_1500 = ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_1800 = ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_2000 = ZonedDateTime.of(2026, 11, 2, 20, 0, 0, 0, ZONE);
    private static final ZonedDateTime MON_2330 = ZonedDateTime.of(2026, 11, 2, 23, 30, 0, 0, ZONE);

    private static final ZonedDateTime TUE_1200 = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, ZONE);

    private static final ZonedDateTime FRI_0100 = ZonedDateTime.of(2026, 11, 6, 1, 0, 0, 0, ZONE);
    private static final ZonedDateTime FRI_2100 = ZonedDateTime.of(2026, 11, 6, 21, 0, 0, 0, ZONE);
    private static final ZonedDateTime FRI_2300 = ZonedDateTime.of(2026, 11, 6, 23, 0, 0, 0, ZONE);

    private static final ZonedDateTime SAT_0100 = ZonedDateTime.of(2026, 11, 7, 1, 0, 0, 0, ZONE);
    private static final ZonedDateTime SAT_0300 = ZonedDateTime.of(2026, 11, 7, 3, 0, 0, 0, ZONE);
    private static final ZonedDateTime SAT_1000 = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, ZONE);
    private static final ZonedDateTime SAT_1200 = ZonedDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZONE);
    private static final ZonedDateTime SAT_1900 = ZonedDateTime.of(2026, 11, 7, 19, 0, 0, 0, ZONE);

    @Test
    void twentyFourSevenIsOpen() {
        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate("24/7", MON_1500));
    }

    @Test
    void dailyTimesAreOpenInsideAndClosedOutside() {
        String hours = "Mo-Su 10:00-19:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_1500));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, MON_2000));
    }

    @Test
    void weekdayRangeIsOpenInsideAndClosedOutside() {
        String hours = "Mo-Fr 09:00-17:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_1000));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, MON_1800));
    }

    @Test
    void commaListDayMatchesOnlyListedDays() {
        String hours = "Sa,Su 10:00-18:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, SAT_1200));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, MON_1200));
    }

    @Test
    void explicitOffDayClosesTheMatchingDay() {
        String hours = "Mo-Su 10:00-18:00; Mo off";

        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, MON_1200));
        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, TUE_1200));
    }

    @Test
    void multipleTimeRangesAreEvaluatedIndependently() {
        String hours = "Mo-Fr 00:00-02:00,12:00-24:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_0100));
        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_1300));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, MON_0300));
        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_2330));
    }

    @Test
    void endOfDayTwentyFourHundredMeansLateNightOpen() {
        String hours = "Mo-Su 10:00-24:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_2330));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, MON_0900));
    }

    @Test
    void overnightRangeCrossesMidnight() {
        String hours = "Fr 22:00-02:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, FRI_2300));
        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, SAT_0100));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, SAT_0300));
        // Friday 01:00 is before the Friday evening opening, not part of the
        // after-midnight tail.
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, FRI_0100));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, FRI_2100));
    }

    @Test
    void unparseableTextIsUnknown() {
        assertEquals(OpeningHours.Status.UNKNOWN,
                OpeningHours.evaluate("sunrise-sunset", MON_1500));
        assertEquals(OpeningHours.Status.UNKNOWN,
                OpeningHours.evaluate("Mo-Fr 9am-5pm", MON_1500));
    }

    @Test
    void blankOrNullIsUnknown() {
        assertEquals(OpeningHours.Status.UNKNOWN, OpeningHours.evaluate(null, MON_1500));
        assertEquals(OpeningHours.Status.UNKNOWN, OpeningHours.evaluate("", MON_1500));
        assertEquals(OpeningHours.Status.UNKNOWN, OpeningHours.evaluate("   ", MON_1500));
    }

    @Test
    void dayNotListedInExplicitDayRulesIsClosed() {
        String hours = "Mo-Fr 09:00-17:00";

        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, SAT_1000));
    }

    @Test
    void ruleWithoutDaySpecAppliesEveryDay() {
        String hours = "10:00-18:00";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, SAT_1200));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, SAT_1900));
    }

    @Test
    void publicHolidayRulesAreIgnoredRatherThanClosingEveryDay() {
        String hours = "Mo-Fr 09:00-17:00; PH off";

        assertEquals(OpeningHours.Status.OPEN, OpeningHours.evaluate(hours, MON_1000));
        assertEquals(OpeningHours.Status.CLOSED, OpeningHours.evaluate(hours, SAT_1000));
    }
}
