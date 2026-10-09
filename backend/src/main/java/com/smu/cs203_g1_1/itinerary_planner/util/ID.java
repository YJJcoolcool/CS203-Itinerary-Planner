package com.smu.cs203_g1_1.itinerary_planner.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

public final class ID {

    private static final String CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final int ID_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ID() {
        // Utility class; prevent instantiation.
    }

    public static String generateID() {
        StringBuilder id = new StringBuilder(ID_LENGTH);

        for (int i = 0; i < ID_LENGTH; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            id.append(CHARACTERS.charAt(index));
        }

        return id.toString();
    }

    public static String generateUniqueID(Predicate<String> exists) {
        String id;

        do {
            id = generateID();
        } while (exists.test(id));

        return id;
    }
}