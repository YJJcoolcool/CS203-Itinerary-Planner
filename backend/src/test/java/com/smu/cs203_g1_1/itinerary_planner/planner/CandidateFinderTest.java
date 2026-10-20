package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;

@DataJpaTest
class CandidateFinderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    /** 2026-11-02 is a Monday. */
    private static final ZonedDateTime ARRIVAL =
            ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZONE);

    private static final String DROPPED_ID = "DropLoc1";
    private static final double DROPPED_LAT = 1.30;
    private static final double DROPPED_LON = 103.85;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlaceScoreRepository placeScoreRepository;

    private CandidateFinder candidateFinder;

    @BeforeEach
    void setUp() {
        candidateFinder = new CandidateFinder(
                locationRepository, placeScoreRepository, new ScoreCalculator());
    }

    @Test
    void excludesClosedOutOfRadiusAndDroppedPlace() {
        saveDropped();
        saveLocation("OpenLc01", 1.30, 103.86, "culture", "INDOOR", "24/7");
        saveLocation("ClosedLc", 1.305, 103.85, "culture", "INDOOR", "Mo-Fr 09:00-12:00");
        saveLocation("FarLoc01", 1.35, 103.85, "culture", "INDOOR", "24/7");

        List<Candidate> result = candidateFinder.find(request(false, Set.of(), 10, Set.of()));

        assertEquals(List.of("OpenLc01"), ids(result));
    }

    @Test
    void unknownHoursAreKeptPenalisedAndFlagged() {
        saveDropped();
        saveLocation("OpenHrs1", 1.31, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("UnknownH", 1.31, 103.85, "culture", "INDOOR", null);

        List<Candidate> result = candidateFinder.find(request(false, Set.of(), 10, Set.of()));

        assertEquals(List.of("OpenHrs1", "UnknownH"), ids(result));

        Candidate open = find(result, "OpenHrs1");
        Candidate unknown = find(result, "UnknownH");

        assertEquals(OpeningHours.Status.OPEN, open.openStatus());
        assertFalse(open.unknownHours());

        assertEquals(OpeningHours.Status.UNKNOWN, unknown.openStatus());
        assertTrue(unknown.unknownHours());
        assertTrue(unknown.score() < open.score());
    }

    @Test
    void requireIndoorExcludesOutdoorAndUnknown() {
        saveDropped();
        saveLocation("Indoor01", 1.31, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("Outdoor1", 1.31, 103.85, "culture", "OUTDOOR", "24/7");
        saveLocation("UnknownI", 1.31, 103.85, "culture", null, "24/7");
        saveLocation("UnknownC", 1.31, 103.85, "culture", "UNKNOWN", "24/7");

        List<Candidate> result = candidateFinder.find(request(true, Set.of(), 10, Set.of()));

        assertEquals(List.of("Indoor01"), ids(result));
    }

    @Test
    void excludesListedLocationIds() {
        saveDropped();
        saveLocation("ExclA01", 1.31, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("ExclB01", 1.31, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("ExclC01", 1.31, 103.85, "culture", "INDOOR", "24/7");

        List<Candidate> result = candidateFinder.find(
                request(false, Set.of(), 10, Set.of("ExclB01", "ExclC01")));

        assertEquals(List.of("ExclA01"), ids(result));
    }

    @Test
    void orderingIsDeterministicAndLimitIsApplied() {
        saveDropped();
        saveLocation("TieIdB", 1.305, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("TieIdA", 1.305, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("TieIdC", 1.305, 103.85, "culture", "INDOOR", "24/7");

        CandidateRequest request = request(false, Set.of(), 2, Set.of());
        List<Candidate> first = candidateFinder.find(request);
        List<Candidate> second = candidateFinder.find(request);

        // Equal score and distance, so the id is the stable final tie-break.
        assertEquals(List.of("TieIdA", "TieIdB"), ids(first));
        assertEquals(ids(first), ids(second));
    }

    @Test
    void manualQualityScoreAffectsOrder() {
        saveDropped();
        saveLocation("HighQual", 1.31, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("LowQual1", 1.31, 103.85, "culture", "INDOOR", "24/7");

        saveScore("HighQual", "0.900");
        saveScore("LowQual1", "0.100");

        List<Candidate> result = candidateFinder.find(request(false, Set.of(), 10, Set.of()));

        assertEquals(List.of("HighQual", "LowQual1"), ids(result));
        assertTrue(find(result, "HighQual").score() > find(result, "LowQual1").score());
    }

    @Test
    void missingPlaceScoreDefaultsToHalf() {
        saveDropped();
        saveLocation("Scored05", 1.31, 103.85, "culture", "INDOOR", "24/7");
        saveLocation("Unscored", 1.31, 103.85, "culture", "INDOOR", "24/7");

        saveScore("Scored05", "0.500");

        List<Candidate> result = candidateFinder.find(request(false, Set.of(), 10, Set.of()));

        assertEquals(2, result.size());
        assertEquals(find(result, "Scored05").score(),
                find(result, "Unscored").score(), 1e-12);
    }

    @Test
    void includesIndexedPlacesThatAreNotCurated() {
        saveDropped();
        saveIndexedLocation("IndexLc1", 1.30, 103.86, "culture", "INDOOR", "24/7");

        List<Candidate> result = candidateFinder.find(request(false, Set.of(), 10, Set.of()));

        assertEquals(List.of("IndexLc1"), ids(result));
    }

    private CandidateRequest request(boolean requireIndoor, Set<String> interests,
                                     int limit, Set<String> excluded) {
        return new CandidateRequest(
                DROPPED_ID, ARRIVAL, requireIndoor, interests, limit, excluded);
    }

    private void saveDropped() {
        saveLocation(DROPPED_ID, DROPPED_LAT, DROPPED_LON,
                "culture", "INDOOR", "24/7");
    }

    private Location saveLocation(String id, double latitude, double longitude,
                                  String categoryGroup, String indoorClass,
                                  String openingHours) {
        Location location = new Location(
                "Place " + id,
                "test",
                latitude,
                longitude,
                "INDOOR".equals(indoorClass));
        location.setId(id);
        location.setSeeded(true);
        location.setCategoryGroup(categoryGroup);
        location.setIndoorClass(indoorClass);
        location.setOpeningHours(openingHours);
        return locationRepository.saveAndFlush(location);
    }

    private Location saveIndexedLocation(String id, double latitude, double longitude,
                                         String categoryGroup, String indoorClass,
                                         String openingHours) {
        Location location = saveLocation(id, latitude, longitude, categoryGroup, indoorClass, openingHours);
        location.setSeeded(false);
        return locationRepository.saveAndFlush(location);
    }

    private void saveScore(String locationId, String quality) {
        PlaceScore score = new PlaceScore(
                locationId,
                "MANUAL",
                new BigDecimal(quality),
                new BigDecimal("0.500"),
                Instant.parse("2026-10-18T02:00:00Z"));
        placeScoreRepository.saveAndFlush(score);
    }

    private static Candidate find(List<Candidate> candidates, String id) {
        return candidates.stream()
                .filter(candidate -> id.equals(candidate.location().getId()))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> ids(List<Candidate> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.location().getId())
                .toList();
    }
}
