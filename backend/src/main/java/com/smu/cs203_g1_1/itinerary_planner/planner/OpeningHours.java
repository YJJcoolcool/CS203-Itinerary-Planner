package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pragmatic, pure evaluation of an OSM {@code opening_hours} string.
 *
 * <p>This deliberately implements only the subset we need, and treats anything
 * else as {@link Status#UNKNOWN} rather than guessing:</p>
 *
 * <ul>
 *   <li>null / blank -&gt; {@code UNKNOWN}</li>
 *   <li>{@code 24/7} -&gt; {@code OPEN}</li>
 *   <li>rules are separated by {@code ;}</li>
 *   <li>an optional day spec precedes the times: a single day ({@code Mo}),
 *       a range ({@code Mo-Fr}), or a comma list ({@code Sa,Su}); {@code PH}
 *       is ignored because we cannot evaluate public holidays</li>
 *   <li>a rule without a day spec applies every day</li>
 *   <li>time ranges are comma-separated, e.g. {@code 10:00-19:00} or
 *       {@code 00:00-02:00,12:00-24:00}</li>
 *   <li>{@code 24:00} may only be used as an end and means end of day</li>
 *   <li>an overnight range (start after end) is open across midnight</li>
 *   <li>{@code off} on a matching day closes that day</li>
 *   <li>when every rule declares explicit days, a day that is not listed is
 *       {@code CLOSED}: the venue told us which days it opens</li>
 *   <li>unparseable or unsupported text -&gt; {@code UNKNOWN}</li>
 * </ul>
 *
 * <p>Times are interpreted in the zone of the supplied instant; this class has
 * no clock and no Spring dependency.</p>
 */
public final class OpeningHours {

    public enum Status {
        OPEN,
        CLOSED,
        UNKNOWN
    }

    private static final int MINUTES_PER_DAY = 24 * 60;

    /** Optional leading day spec followed by the time spec. */
    private static final Pattern DAY_PREFIX = Pattern.compile(
            "^([A-Za-z]{2}(?:-[A-Za-z]{2})?(?:,[A-Za-z]{2}(?:-[A-Za-z]{2})?)*)\\s+(.*)$");

    private static final Pattern TIME_RANGE = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})$");

    private static final Rule IGNORED_RULE =
            new Rule(Set.of(), false, false, List.of(), true);

    private OpeningHours() {
    }

    public static Status evaluate(String raw, ZonedDateTime at) {
        if (raw == null || raw.isBlank()) {
            return Status.UNKNOWN;
        }

        String trimmed = raw.trim();
        if ("24/7".equalsIgnoreCase(trimmed)) {
            return Status.OPEN;
        }
        if (at == null) {
            return Status.UNKNOWN;
        }

        List<Rule> rules = parseRules(trimmed);
        if (rules == null) {
            // Unsupported or malformed text. Never silently report CLOSED.
            return Status.UNKNOWN;
        }

        DayOfWeek today = at.getDayOfWeek();
        int minute = at.toLocalTime().toSecondOfDay() / 60;

        // An explicit "off" closes the whole matching day, even if another rule
        // on the same day would otherwise have an open interval.
        for (Rule rule : rules) {
            if (rule.off() && rule.appliesTo(today)) {
                return Status.CLOSED;
            }
        }

        // Current-day intervals. An overnight range only covers the part from
        // its start until midnight on its own day; the after-midnight part is
        // handled by the previous-day pass below.
        for (Rule rule : rules) {
            if (!rule.off() && rule.appliesTo(today)) {
                for (TimeRange range : rule.ranges()) {
                    if (range.openOnStartDay(minute)) {
                        return Status.OPEN;
                    }
                }
            }
        }

        // After midnight: yesterday's overnight range may still be open.
        DayOfWeek yesterday = today.minus(1);
        for (Rule rule : rules) {
            if (!rule.off() && rule.appliesTo(yesterday)) {
                for (TimeRange range : rule.ranges()) {
                    if (range.openOnNextDay(minute)) {
                        return Status.OPEN;
                    }
                }
            }
        }

        // The text parsed and the venue declared its schedule, so outside the
        // listed intervals/days the place is closed.
        return Status.CLOSED;
    }

    private static List<Rule> parseRules(String raw) {
        List<Rule> rules = new ArrayList<>();

        for (String part : raw.split(";")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }

            Rule rule = parseRule(text);
            if (rule == null) {
                return null;
            }
            if (!rule.ignored()) {
                rules.add(rule);
            }
        }

        return rules.isEmpty() ? null : rules;
    }

    private static Rule parseRule(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        boolean dayless = true;
        String timePart = text;

        Matcher dayMatcher = DAY_PREFIX.matcher(text);
        if (dayMatcher.matches()) {
            DaySpec daySpec = parseDays(dayMatcher.group(1));
            if (daySpec == null) {
                return null;
            }
            if (daySpec.phOnly()) {
                // A rule that only talks about public holidays cannot be
                // evaluated for a normal weekday, so skip it.
                return IGNORED_RULE;
            }
            days = daySpec.days();
            dayless = false;
            timePart = dayMatcher.group(2).trim();
        }

        if (timePart.isEmpty()) {
            return null;
        }

        if ("off".equalsIgnoreCase(timePart)) {
            return new Rule(days, dayless, true, List.of(), false);
        }

        if ("24/7".equalsIgnoreCase(timePart)) {
            return new Rule(days, dayless, false,
                    List.of(new TimeRange(0, MINUTES_PER_DAY)), false);
        }

        List<TimeRange> ranges = new ArrayList<>();
        for (String piece : timePart.split(",")) {
            Matcher timeMatcher = TIME_RANGE.matcher(piece.trim());
            if (!timeMatcher.matches()) {
                return null;
            }

            int start = parseMinute(timeMatcher.group(1), timeMatcher.group(2), false);
            int end = parseMinute(timeMatcher.group(3), timeMatcher.group(4), true);
            if (start < 0 || end < 0) {
                return null;
            }
            ranges.add(new TimeRange(start, end));
        }

        return ranges.isEmpty() ? null : new Rule(days, dayless, false, ranges, false);
    }

    private static DaySpec parseDays(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        boolean sawPublicHoliday = false;

        for (String token : text.split(",")) {
            String value = token.trim();

            if ("PH".equalsIgnoreCase(value)) {
                sawPublicHoliday = true;
                continue;
            }

            int dash = value.indexOf('-');
            if (dash > 0) {
                DayOfWeek start = dayOfWeek(value.substring(0, dash));
                DayOfWeek end = dayOfWeek(value.substring(dash + 1));
                if (start == null || end == null) {
                    return null;
                }

                DayOfWeek current = start;
                boolean reachedEnd = false;
                for (int i = 0; i < 7; i++) {
                    days.add(current);
                    if (current == end) {
                        reachedEnd = true;
                        break;
                    }
                    current = current.plus(1);
                }
                if (!reachedEnd) {
                    return null;
                }
            } else {
                DayOfWeek day = dayOfWeek(value);
                if (day == null) {
                    return null;
                }
                days.add(day);
            }
        }

        if (days.isEmpty()) {
            return sawPublicHoliday ? new DaySpec(Set.of(), true) : null;
        }
        return new DaySpec(days, false);
    }

    private static DayOfWeek dayOfWeek(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "mo" -> DayOfWeek.MONDAY;
            case "tu" -> DayOfWeek.TUESDAY;
            case "we" -> DayOfWeek.WEDNESDAY;
            case "th" -> DayOfWeek.THURSDAY;
            case "fr" -> DayOfWeek.FRIDAY;
            case "sa" -> DayOfWeek.SATURDAY;
            case "su" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static int parseMinute(String hourText, String minuteText, boolean allowEndOfDay) {
        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(minuteText);

        if (minute < 0 || minute > 59) {
            return -1;
        }
        if (hour == 24) {
            if (!allowEndOfDay || minute != 0) {
                return -1;
            }
            return MINUTES_PER_DAY;
        }
        if (hour < 0 || hour > 23) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private record DaySpec(Set<DayOfWeek> days, boolean phOnly) {
    }

    private record Rule(Set<DayOfWeek> days, boolean dayless, boolean off,
                        List<TimeRange> ranges, boolean ignored) {

        boolean appliesTo(DayOfWeek day) {
            return dayless || days.contains(day);
        }
    }

    private record TimeRange(int start, int end) {

        boolean openOnStartDay(int minute) {
            if (start <= end) {
                return minute >= start && minute < end;
            }
            // Overnight: [start, 24:00) on the rule's own day.
            return minute >= start;
        }

        boolean openOnNextDay(int minute) {
            // Overnight: [00:00, end) on the following day.
            return start > end && minute < end;
        }
    }
}
