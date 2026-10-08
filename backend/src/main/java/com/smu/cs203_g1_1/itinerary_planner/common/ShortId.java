package com.smu.cs203_g1_1.itinerary_planner.common;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import java.security.SecureRandom;

/**
 * Generates 8-character alphanumeric IDs (e.g. "aZ3k9QxP") for entities.
 * 62^8 ≈ 2.2e14 possible values, so collisions are negligible at our scale.
 *
 * NOTE: groupmate is migrating the other entities to 8-char IDs. If they introduce
 * their own generator, replace calls to this class with theirs and delete it.
 */
public final class ShortId {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHANUMERIC =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int LENGTH = 8;

    private ShortId() {
    }

    public static String generate() {
        return NanoIdUtils.randomNanoId(RANDOM, ALPHANUMERIC, LENGTH);
    }
}
