package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class OsmIndexImporterTest {

    private static final String FIXTURE = """
        { "type": "FeatureCollection", "features": [
          { "type": "Feature",
            "properties": {"@type":"node","@id":101,"name":"Test Hawker",
              "amenity":"food_court","building":"yes",
              "addr:housenumber":"1","addr:street":"Test Road","addr:postcode":"123456"},
            "geometry": {"type":"Point","coordinates":[103.85,1.30]} },
          { "type": "Feature",
            "properties": {"@type":"way","@id":202,"name":"Test Museum",
              "tourism":"museum","opening_hours":"Mo-Su 10:00-19:00"},
            "geometry": {"type":"Polygon","coordinates":[[[103.85,1.29],[103.86,1.29],
              [103.86,1.30],[103.85,1.30],[103.85,1.29]]]} },
          { "type": "Feature",
            "properties": {"@type":"node","@id":303,"tourism":"museum"},
            "geometry": {"type":"Point","coordinates":[103.85,1.30]} },
          { "type": "Feature",
            "properties": {"@type":"node","@id":404,"name":"Tokyo Cafe","amenity":"cafe"},
            "geometry": {"type":"Point","coordinates":[139.65,35.67]} },
          { "type": "Feature",
            "properties": {"@type":"node","@id":505,"name":"Test Bench","amenity":"bench"},
            "geometry": {"type":"Point","coordinates":[103.85,1.30]} }
        ]}
        """;

    @Autowired
    private LocationRepository locations;

    private OsmIndexImporter importer;

    @BeforeEach
    void setUp() {
        importer = new OsmIndexImporter(locations, new RegionRegistry());
    }

    @Test
    void importsOnlyUsableSingaporeFeatures() {
        OsmIndexImporter.ImportSummary summary = importer.importFrom(stream(FIXTURE));

        assertEquals(5, summary.total());
        assertEquals(2, summary.imported());
        assertEquals(1, summary.unnamed());
        assertEquals(1, summary.outsideRegion());
        assertEquals(1, summary.unknownCategory());
        assertEquals(2, locations.count());
    }

    @Test
    void mapsGroupIndoorEvidenceAndAddress() {
        importer.importFrom(stream(FIXTURE));

        Location hawker = locations.findByOsmTypeAndOsmId("node", 101L).orElseThrow();

        assertFalse(hawker.isSeeded());
        assertEquals("food_court", hawker.getCategory());
        assertEquals("food", hawker.getCategoryGroup());
        assertEquals("INDOOR", hawker.getIndoorClass());
        assertEquals("OSM_TAG", hawker.getIndoorSource());
        assertEquals("sg", hawker.getCountryCode());
        assertTrue(hawker.getAddress().contains("Test Road"));
        assertTrue(hawker.getAddress().contains("123456"));
    }

    @Test
    void polygonIsReducedToItsCentreAndKeepsHours() {
        importer.importFrom(stream(FIXTURE));

        Location museum = locations.findByOsmTypeAndOsmId("way", 202L).orElseThrow();

        assertEquals("culture", museum.getCategoryGroup());
        assertEquals(1.294, museum.getLatitude().doubleValue(), 0.01);
        assertEquals(103.855, museum.getLongitude().doubleValue(), 0.01);
        assertEquals("Mo-Su 10:00-19:00", museum.getOpeningHours());
    }

    @Test
    void secondImportAddsNothing() {
        importer.importFrom(stream(FIXTURE));

        OsmIndexImporter.ImportSummary second = importer.importFrom(stream(FIXTURE));

        assertEquals(0, second.imported());
        assertEquals(2, second.duplicate());
        assertEquals(2, locations.count());
    }

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
