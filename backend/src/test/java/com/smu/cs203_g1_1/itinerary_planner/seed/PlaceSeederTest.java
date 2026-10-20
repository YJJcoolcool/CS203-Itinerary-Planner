package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.smu.cs203_g1_1.itinerary_planner.seed.PlaceSeeder.SeedResult;
import com.smu.cs203_g1_1.itinerary_planner.seed.SgPoiSeed.Place;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreId;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;

@DataJpaTest
class PlaceSeederTest {

    private static final int CURATED_PLACE_COUNT = 49;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlaceScoreRepository placeScoreRepository;

    private PlaceSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new PlaceSeeder(locationRepository, placeScoreRepository);
    }

    @Test
    void classpathSeedInsertsCuratedPlacesAndManualScores() {
        SeedResult result = seeder.seed();

        assertEquals(CURATED_PLACE_COUNT, result.inserted());
        assertEquals(0, result.skipped());
        assertEquals(CURATED_PLACE_COUNT, result.scoresInserted());

        List<Location> locations = locationRepository.findAll();
        assertEquals(CURATED_PLACE_COUNT, locations.size());

        for (Location location : locations) {
            assertTrue(location.isSeeded(), "location should be marked seeded: " + location.getId());
            assertNotNull(location.getCategoryGroup(),
                    "seeded location should have a category group: " + location.getId());
        }

        List<PlaceScore> scores = placeScoreRepository.findAll();
        assertEquals(CURATED_PLACE_COUNT, scores.size());
        assertTrue(scores.stream().allMatch(score -> "MANUAL".equals(score.getSource())));
    }

    @Test
    void seedingTwiceIsIdempotent() {
        seeder.seed();

        SeedResult second = seeder.seed();

        assertEquals(0, second.inserted());
        assertEquals(CURATED_PLACE_COUNT, second.skipped());
        assertEquals(0, second.scoresInserted());
        assertEquals(CURATED_PLACE_COUNT, locationRepository.count());
        assertEquals(CURATED_PLACE_COUNT, placeScoreRepository.count());
    }

    @Test
    void existingLocationIsNotOverwrittenButMissingManualScoreIsInserted() {
        Location existing = new Location("Hand-edited name", "museum", 1.2966, 103.8485, true);
        existing.setId("Exist001");
        existing.setOsmType("node");
        existing.setOsmId(123456L);
        existing.setCategoryGroup("culture");
        locationRepository.saveAndFlush(existing);

        SeedResult result = seeder.seed(seedWith(place("Seed name")));

        assertEquals(0, result.inserted());
        assertEquals(1, result.skipped());
        assertEquals(1, result.scoresInserted());

        Location found = locationRepository.findById("Exist001").orElseThrow();
        assertEquals("Hand-edited name", found.getName());
        assertEquals(1, locationRepository.count());

        PlaceScore score = placeScoreRepository
                .findById(new PlaceScoreId("Exist001", "MANUAL"))
                .orElseThrow();
        assertEquals(0, score.getQualityScore().compareTo(new BigDecimal("0.8")));
        assertEquals(0, score.getPopularityScore().compareTo(new BigDecimal("0.7")));
    }

    @Test
    void malformedSeedFailsFastWithUsefulMessage() {
        assertInvalid(seedWith(place(" ")), "name");
        assertInvalid(seedWith(place("Duplicate"), place("Duplicate")), "duplicate");
        assertInvalid(seedWith(placeWithLatitude(1.99)), "latitude");
        assertInvalid(seedWith(placeWithGroup("bowling")), "category_group");

        assertEquals(0, locationRepository.count());
        assertEquals(0, placeScoreRepository.count());
    }

    private void assertInvalid(SgPoiSeed seed, String expectedMessagePart) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> seeder.seed(seed));
        assertTrue(exception.getMessage().contains(expectedMessagePart),
                "message should mention '" + expectedMessagePart + "': " + exception.getMessage());
    }

    private static SgPoiSeed seedWith(Place... places) {
        return new SgPoiSeed(
                "OpenStreetMap via test",
                "2026-10-17",
                "ODbL-1.0",
                "© OpenStreetMap contributors",
                List.of(places));
    }

    private static Place place(String name) {
        return new Place(
                "node",
                123456L,
                name,
                "museum",
                "culture",
                1.2966,
                103.8485,
                "INDOOR",
                "MANUAL",
                "Mo-Su 10:00-19:00",
                "93 Stamford Road Singapore",
                new BigDecimal("10"),
                new BigDecimal("0.8"),
                new BigDecimal("0.7"),
                Map.of("tourism", "museum"));
    }

    private static Place placeWithLatitude(double latitude) {
        Place base = place("Out of range");
        return new Place(
                base.osmType(), base.osmId(), base.name(), base.category(), base.categoryGroup(),
                latitude, base.longitude(), base.indoorClass(), base.indoorSource(),
                base.openingHours(), base.address(), base.estCost(), base.qualityScore(),
                base.popularityScore(), base.tags());
    }

    private static Place placeWithGroup(String categoryGroup) {
        Place base = place("Unknown group");
        return new Place(
                base.osmType(), base.osmId(), base.name(), base.category(), categoryGroup,
                base.latitude(), base.longitude(), base.indoorClass(), base.indoorSource(),
                base.openingHours(), base.address(), base.estCost(), base.qualityScore(),
                base.popularityScore(), base.tags());
    }
}
