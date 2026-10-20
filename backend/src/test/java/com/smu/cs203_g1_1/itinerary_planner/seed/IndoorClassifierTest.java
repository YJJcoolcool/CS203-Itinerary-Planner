package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.smu.cs203_g1_1.itinerary_planner.seed.IndoorClassifier.IndoorClassification;

class IndoorClassifierTest {

    private final IndoorClassifier classifier = new IndoorClassifier();

    @Test
    void explicitIndoorTag_isIndoor() {
        assertClassification("INDOOR", "OSM_TAG", 0.95,
                classifier.classify("attraction", Map.of("indoor", "yes")));
    }

    @Test
    void explicitIndoorWinsOverConflictingOutdoorCategory() {
        // Rule 1 must beat rule 4 (leisure=park).
        assertClassification("INDOOR", "OSM_TAG", 0.95,
                classifier.classify("nature", Map.of("indoor", "yes", "leisure", "park")));
    }

    @Test
    void coveredYes_isIndoor() {
        assertClassification("INDOOR", "OSM_TAG", 0.95,
                classifier.classify("attraction", Map.of("covered", "yes")));
    }

    @Test
    void mall_isIndoor() {
        assertClassification("INDOOR", "OSM_TAG", 0.9,
                classifier.classify("shopping", Map.of("shop", "mall")));
    }

    @Test
    void museum_isIndoor() {
        assertClassification("INDOOR", "OSM_TAG", 0.9,
                classifier.classify("culture", Map.of("tourism", "museum")));
    }

    @Test
    void restaurant_isIndoor() {
        assertClassification("INDOOR", "OSM_TAG", 0.85,
                classifier.classify("food", Map.of("amenity", "restaurant")));
    }

    @Test
    void park_isOutdoor() {
        assertClassification("OUTDOOR", "OSM_TAG", 0.85,
                classifier.classify("nature", Map.of("leisure", "park")));
    }

    @Test
    void viewpoint_isOutdoor() {
        assertClassification("OUTDOOR", "OSM_TAG", 0.85,
                classifier.classify("attraction", Map.of("tourism", "viewpoint")));
    }

    @Test
    void naturalTag_isOutdoor() {
        assertClassification("OUTDOOR", "OSM_TAG", 0.85,
                classifier.classify("nature", Map.of("natural", "wood")));
    }

    @Test
    void attraction_isUnknown() {
        assertClassification("UNKNOWN", null, 0.3,
                classifier.classify("attraction", Map.of("tourism", "attraction")));
    }

    @Test
    void placeOfWorship_isUnknown() {
        assertClassification("UNKNOWN", null, 0.3,
                classifier.classify("culture", Map.of("amenity", "place_of_worship")));
    }

    @Test
    void buildingOnly_isWeakIndoor() {
        assertClassification("INDOOR", "OSM_TAG", 0.6,
                classifier.classify("attraction", Map.of("building", "yes")));
    }

    @Test
    void nullTags_isUnknownWithZeroConfidence() {
        assertClassification("UNKNOWN", null, 0.0,
                classifier.classify("attraction", null));
    }

    @Test
    void emptyTagsAndBlankCategory_isUnknownWithZeroConfidence() {
        assertClassification("UNKNOWN", null, 0.0,
                classifier.classify("  ", Map.of()));
    }

    private static void assertClassification(String expectedClass, String expectedSource,
                                             double expectedConfidence, IndoorClassification actual) {
        assertEquals(expectedClass, actual.indoorClass());
        assertEquals(expectedSource, actual.source());
        assertEquals(expectedConfidence, actual.confidence(), 1e-9);
    }
}
