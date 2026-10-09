package com.smu.cs203_g1_1.itinerary_planner.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IDTest {

    @Test
    void generateID_shouldGenerateEightAlphanumericCharacters() {
        String id = ID.generateID();

        assertEquals(8, id.length());
        assertTrue(id.matches("[A-Za-z0-9]{8}"));
    }

    @Test
    void generateID_shouldGenerateDifferentIds() {
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            ids.add(ID.generateID());
        }

        assertEquals(1000, ids.size());
    }
}