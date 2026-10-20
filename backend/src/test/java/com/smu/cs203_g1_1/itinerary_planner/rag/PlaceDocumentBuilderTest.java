package com.smu.cs203_g1_1.itinerary_planner.rag;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PlaceDocumentBuilderTest {

    @Test
    void describesThePlaceInWords() {
        String document = PlaceDocumentBuilder.build(
            location("National Museum", "museum", "culture", "INDOOR"));

        assertTrue(document.contains("National Museum"));
        assertTrue(document.contains("museum"));
        assertTrue(document.contains("culture"));
        assertTrue(document.contains("Indoors"));
    }

    @Test
    void marksOutdoorPlacesAndKeepsAddressAndHours() {
        Location park = location("Botanic Gardens", "park", "nature", "OUTDOOR");
        park.setAddress("1 Cluny Road");
        park.setOpeningHours("Mo-Su 05:00-00:00");

        String document = PlaceDocumentBuilder.build(park);

        assertTrue(document.contains("Outdoors"));
        assertTrue(document.contains("Address: 1 Cluny Road."));
        assertTrue(document.contains("Opening hours: Mo-Su 05:00-00:00."));
    }

    @Test
    void neverEmbedsCoordinates() {
        Location place = location("Somewhere", "cafe", "food", "INDOOR");
        place.setLatitude(1.3048);
        place.setLongitude(103.8318);

        String document = PlaceDocumentBuilder.build(place);

        assertFalse(document.contains("1.3048"));
        assertFalse(document.contains("103.8318"));
    }

    @Test
    void toleratesMissingFields() {
        Location bare = new Location();
        bare.setId("loc00001");
        bare.setName("Unnamed kiosk");

        String document = PlaceDocumentBuilder.build(bare);

        assertEquals("Unnamed kiosk.", document);
    }

    @Test
    void emptyInputProducesAnEmptyDocument() {
        assertEquals("", PlaceDocumentBuilder.build(null));
    }

    private static Location location(String name, String category, String group, String indoor) {
        Location location = new Location();
        location.setId("loc00001");
        location.setName(name);
        location.setCategory(category);
        location.setCategoryGroup(group);
        location.setIndoorClass(indoor);

        return location;
    }
}
