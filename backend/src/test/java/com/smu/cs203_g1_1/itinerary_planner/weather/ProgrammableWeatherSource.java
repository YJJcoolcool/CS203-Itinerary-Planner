package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Test double for {@link WeatherSource}: the answer is driven by a settable
 * rule and every invocation is recorded so a test can assert the gate ran with
 * the expected point and window. Not a Spring bean; tests instantiate it.
 */
public class ProgrammableWeatherSource implements WeatherSource {

    public record Call(double latitude, double longitude,
                       ZonedDateTime from, ZonedDateTime to) {
    }

    private final String id;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile Predicate<Call> rule = call -> false;

    public ProgrammableWeatherSource() {
        this("fixture");
    }

    public ProgrammableWeatherSource(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isRainy(double latitude, double longitude,
                           ZonedDateTime from, ZonedDateTime to) {
        Call call = new Call(latitude, longitude, from, to);
        calls.add(call);
        return rule.test(call);
    }

    public void setRainy(boolean rainy) {
        setRule(call -> rainy);
    }

    public void setRule(Predicate<Call> rule) {
        this.rule = rule == null ? call -> false : rule;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
        rule = call -> false;
    }
}
