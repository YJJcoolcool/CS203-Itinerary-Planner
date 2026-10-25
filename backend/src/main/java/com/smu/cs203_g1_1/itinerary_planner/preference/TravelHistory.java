package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.util.List;

/**
 * What a user's own itineraries say about them: how many trips they are on,
 * the kinds of places they plan most, and the places themselves. Fed into the
 * personal preference summary alongside what they told us
 */
public record TravelHistory(int tripCount, List<String> topCategories, List<String> places) {

    public static final TravelHistory EMPTY = new TravelHistory(0, List.of(), List.of());

    public boolean isEmpty() {
        return places.isEmpty();
    }
}