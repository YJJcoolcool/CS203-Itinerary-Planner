package com.smu.cs203_g1_1.itinerary_planner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreId;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;

@DataJpaTest
class PlaceSeedRepositoryTest {

    private static final double MIN_LAT = 1.25;
    private static final double MAX_LAT = 1.35;
    private static final double MIN_LON = 103.80;
    private static final double MAX_LON = 103.90;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlaceScoreRepository placeScoreRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void seededLocationWithNewFieldsRoundTrips() {

        Location location = new Location(
            "National Museum of Singapore",
            "museum",
            1.2966,
            103.8485,
            true
        );

        location.setId("SeedLocA");
        location.setCategoryGroup("culture");
        location.setIndoorClass("INDOOR");
        location.setIndoorSource("OSM_TAG");
        location.setIndoorConfidence(0.92);
        location.setEstCost(new BigDecimal("12.50"));
        location.setOpenIntervals("Mo-Su 10:00-19:00");
        location.setSeeded(true);

        locationRepository.saveAndFlush(location);
        entityManager.clear();

        Location found = locationRepository.findById("SeedLocA").orElseThrow();

        assertEquals("culture", found.getCategoryGroup());
        assertEquals("INDOOR", found.getIndoorClass());
        assertEquals("OSM_TAG", found.getIndoorSource());
        assertEquals(0.92, found.getIndoorConfidence());
        assertEquals(0, found.getEstCost().compareTo(new BigDecimal("12.50")));
        assertEquals("Mo-Su 10:00-19:00", found.getOpenIntervals());
        assertTrue(found.isSeeded());
    }

    @Test
    void findSeededInBoundingBoxReturnsOnlySeededRowsInsideTheBox() {

        Location inBox = seededLocation("SeedLocA", 1.30, 103.85);
        Location outOfBox = seededLocation("SeedLocB", 1.45, 103.85);

        // New locations are not seeded by default.
        Location notSeeded = new Location("Manual cafe", "cafe", 1.31, 103.86, true);
        notSeeded.setId("SeedLocC");

        locationRepository.saveAllAndFlush(List.of(inBox, outOfBox, notSeeded));
        entityManager.clear();

        List<Location> result = locationRepository.findSeededInBoundingBox(
            MIN_LAT, MAX_LAT, MIN_LON, MAX_LON
        );

        assertEquals(1, result.size());
        assertEquals("SeedLocA", result.get(0).getId());

        // Both seeded rows are still visible without the box filter.
        assertEquals(2, locationRepository.findBySeededTrue().size());
    }

    @Test
    void placeScoresRoundTripWithCompositeKey() {

        locationRepository.saveAndFlush(seededLocation("SeedLocA", 1.30, 103.85));

        Instant scoredAt = Instant.parse("2026-10-18T02:00:00Z");

        PlaceScore score = new PlaceScore(
            "SeedLocA",
            "MANUAL",
            new BigDecimal("0.900"),
            new BigDecimal("0.750"),
            scoredAt
        );

        placeScoreRepository.saveAndFlush(score);
        entityManager.clear();

        PlaceScore found = placeScoreRepository.findById(new PlaceScoreId("SeedLocA", "MANUAL"))
            .orElseThrow();

        assertEquals("SeedLocA", found.getLocationId());
        assertEquals("MANUAL", found.getSource());
        assertEquals(0, found.getQualityScore().compareTo(new BigDecimal("0.900")));
        assertEquals(0, found.getPopularityScore().compareTo(new BigDecimal("0.750")));
        assertEquals(scoredAt, found.getScoredAt());

        List<PlaceScore> scores = placeScoreRepository.findByLocationIdIn(List.of("SeedLocA", "SeedLocB"));
        assertEquals(1, scores.size());
        assertEquals("SeedLocA", scores.get(0).getLocationId());
    }

    private Location seededLocation(String id, double latitude, double longitude) {
        Location location = new Location("Seeded place " + id, "museum", latitude, longitude, true);
        location.setId(id);
        location.setSeeded(true);
        return location;
    }
}
