package com.smu.cs203_g1_1.itinerary_planner.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CategoryGroupsTest {

    private static final Map<String, String> EXPECTED_GROUPS = expectedGroups();

    @Test
    void mapsEveryKnownTagPair() {
        for (Map.Entry<String, String> entry : EXPECTED_GROUPS.entrySet()) {
            String[] tag = entry.getKey().split(":", 2);
            assertEquals(entry.getValue(), CategoryGroups.from(tag[0], tag[1]), entry.getKey());
        }
    }

    @Test
    void normalisesKeyAndValue() {
        assertEquals("culture", CategoryGroups.from("  TOURISM ", " Museum "));
        assertEquals("nature", CategoryGroups.from("LEISURE", "Park"));
        assertEquals("shopping", CategoryGroups.from(" Shop ", " Mall "));
        assertEquals("food", CategoryGroups.from("AMENITY", "RESTAURANT"));
        assertEquals("tourism", CategoryGroups.inferKey("  MUSEUM "));
    }

    @Test
    void infersKeyFromValueWhenKeyIsMissing() {
        assertEquals("culture", CategoryGroups.from(null, "museum"));
        assertEquals("nature", CategoryGroups.from(null, "park"));
        assertEquals("shopping", CategoryGroups.from(null, "mall"));
        assertEquals("food", CategoryGroups.from(null, "restaurant"));
        assertEquals("tourism", CategoryGroups.inferKey("museum"));
        assertEquals("leisure", CategoryGroups.inferKey("park"));
        assertEquals("shop", CategoryGroups.inferKey("mall"));
        assertEquals("amenity", CategoryGroups.inferKey("restaurant"));
    }

    @Test
    void returnsNullForUnknownOrMissingInputs() {
        assertNull(CategoryGroups.from(null, null));
        assertNull(CategoryGroups.from("tourism", null));
        assertNull(CategoryGroups.from("tourism", "hotel"));
        assertNull(CategoryGroups.from(null, "hotel"));
        assertNull(CategoryGroups.from("amenity", "bench"));
        assertNull(CategoryGroups.from("  ", "  "));
        assertNull(CategoryGroups.inferKey(null));
        assertNull(CategoryGroups.inferKey("hotel"));
    }

    private static Map<String, String> expectedGroups() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("tourism:museum", "culture");
        expected.put("tourism:gallery", "culture");
        expected.put("tourism:attraction", "attraction");
        expected.put("tourism:theme_park", "attraction");
        expected.put("tourism:zoo", "nature");
        expected.put("tourism:viewpoint", "nature");
        expected.put("leisure:park", "nature");
        expected.put("leisure:garden", "nature");
        expected.put("leisure:nature_reserve", "nature");
        expected.put("leisure:bowling_alley", "attraction");
        expected.put("shop:mall", "shopping");
        expected.put("shop:department_store", "shopping");
        expected.put("amenity:restaurant", "food");
        expected.put("amenity:cafe", "food");
        expected.put("amenity:fast_food", "food");
        expected.put("amenity:food_court", "food");
        expected.put("amenity:marketplace", "food");
        expected.put("amenity:library", "culture");
        expected.put("amenity:cinema", "culture");
        expected.put("amenity:bar", "nightlife");
        expected.put("amenity:nightclub", "nightlife");
        expected.put("amenity:place_of_worship", "culture");
        return expected;
    }
}
