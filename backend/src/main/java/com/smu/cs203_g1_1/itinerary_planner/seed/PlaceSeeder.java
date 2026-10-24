package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.cs203_g1_1.itinerary_planner.planner.CategorySimilarity;
import com.smu.cs203_g1_1.itinerary_planner.seed.SgPoiSeed.Place;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreId;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

/**
 * Imports the curated POI seed into {@code location} and {@code place_scores}.
 *
 * <p>The import is idempotent by OSM identity: an existing location is never
 * overwritten (manual edits win), and only a missing {@code MANUAL} score is
 * added. Validation happens before any write, so a corrupt seed fails fast and
 * leaves the database untouched.</p>
 */
@Service
public class PlaceSeeder {

    private static final Logger log = LoggerFactory.getLogger(PlaceSeeder.class);

    static final String SEED_RESOURCE = "seed/sg_pois.json";

    private static final Set<String> OSM_TYPES = Set.of("node", "way", "relation");
    private static final Set<String> INDOOR_CLASSES = Set.of("INDOOR", "OUTDOOR", "UNKNOWN");
    private static final Set<String> INDOOR_SOURCES = Set.of("OSM_TAG", "MANUAL", "AI");

    private static final double MIN_LATITUDE = 1.15;
    private static final double MAX_LATITUDE = 1.48;
    private static final double MIN_LONGITUDE = 103.6;
    private static final double MAX_LONGITUDE = 104.1;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final LocationRepository locationRepository;
    private final PlaceScoreRepository placeScoreRepository;
    private final IndoorClassifier indoorClassifier = new IndoorClassifier();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlaceSeeder(LocationRepository locationRepository,
                       PlaceScoreRepository placeScoreRepository) {
        this.locationRepository = locationRepository;
        this.placeScoreRepository = placeScoreRepository;
    }

    /** Loads the classpath seed and imports it. */
    @Transactional
    public SeedResult seed() {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Seed resource '" + SEED_RESOURCE + "' is missing from the classpath");
        }

        SgPoiSeed seed;
        try (InputStream input = resource.getInputStream()) {
            seed = objectMapper.readValue(input, SgPoiSeed.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read seed resource '" + SEED_RESOURCE + "': " + e.getMessage(), e);
        }

        return seed(seed);
    }

    /** Testable core: validates and imports an already-parsed seed. */
    @Transactional
    public SeedResult seed(SgPoiSeed seed) {
        validate(seed);

        int inserted = 0;
        int skipped = 0;
        int scoresInserted = 0;

        for (Place place : seed.places()) {
            Location existing = locationRepository
                    .findByOsmTypeAndOsmId(place.osmType(), place.osmId())
                    .orElse(null);

            if (existing != null) {
                skipped++;
                if (insertManualScoreIfMissing(existing.getId(), place)) {
                    scoresInserted++;
                }
                continue;
            }

            Location location = toLocation(place);
            location.setId(ID.generateUniqueID(locationRepository::existsById));
            locationRepository.save(location);

            placeScoreRepository.save(manualScore(location.getId(), place));
            inserted++;
            scoresInserted++;
        }

        SeedResult result = new SeedResult(inserted, skipped, scoresInserted);
        log.info("Place seed complete: inserted={}, skipped={}, scoresInserted={}",
                result.inserted(), result.skipped(), result.scoresInserted());
        return result;
    }

    private boolean insertManualScoreIfMissing(String locationId, Place place) {
        PlaceScoreId scoreId = new PlaceScoreId(locationId, "MANUAL");

        if (placeScoreRepository.existsById(scoreId)) {
            return false;
        }

        placeScoreRepository.save(manualScore(locationId, place));
        return true;
    }

    private PlaceScore manualScore(String locationId, Place place) {
        return new PlaceScore(
                locationId,
                "MANUAL",
                place.qualityScore(),
                place.popularityScore(),
                Instant.now());
    }

    private Location toLocation(Place place) {
        Location location = new Location();
        location.setName(place.name());
        location.setCategory(place.category());
        location.setLatitude(place.latitude());
        location.setLongitude(place.longitude());
        location.setAddress(place.address());
        location.setOpeningHours(place.openingHours());
        location.setOsmType(place.osmType());
        location.setOsmId(place.osmId());
        location.setCategoryGroup(place.categoryGroup());
        location.setEstCost(place.estCost());
        location.setSeeded(true);
        // openIntervals stays null: parsing opening_hours is a later milestone.

        applyIndoorClassification(location, place);
        return location;
    }

    private void applyIndoorClassification(Location location, Place place) {
        String indoorClass = place.indoorClass();
        String indoorSource = normaliseIndoorSource(place.indoorSource());

        if ("MANUAL".equals(indoorSource)) {
            location.setIndoorClass(indoorClass);
            location.setIndoorSource(indoorSource);
            location.setIndoorConfidence(1.0);
            return;
        }

        if (isBlank(indoorClass)) {
            IndoorClassifier.IndoorClassification classification =
                    indoorClassifier.classify(place.category(), place.tags());
            location.setIndoorClass(classification.indoorClass());
            location.setIndoorSource(classification.source());
            location.setIndoorConfidence(classification.confidence());
            return;
        }

        location.setIndoorClass(indoorClass);
        location.setIndoorSource(indoorSource);
        location.setIndoorConfidence(0.9);
    }

    private void validate(SgPoiSeed seed) {
        if (seed == null) {
            throw new IllegalStateException("Seed is null");
        }

        requireText(seed.source(), "metadata.source");
        requireText(seed.extractDate(), "metadata.extract_date");
        requireText(seed.license(), "metadata.license");

        List<Place> places = seed.places();
        if (places == null || places.isEmpty()) {
            throw new IllegalStateException("Seed contains no places");
        }

        Set<String> identities = new HashSet<>();

        for (int index = 0; index < places.size(); index++) {
            Place place = places.get(index);
            String prefix = placePrefix(index, place);

            if (place == null) {
                throw new IllegalStateException("Seed place [" + index + "] is null");
            }

            if (place.osmType() == null || !OSM_TYPES.contains(place.osmType())) {
                throw new IllegalStateException(
                        prefix + "field 'osm_type' must be one of node, way, relation");
            }
            if (place.osmId() == null) {
                throw new IllegalStateException(prefix + "field 'osm_id' must not be null");
            }
            requireText(place.name(), prefix + "field 'name'");
            requireText(place.category(), prefix + "field 'category'");

            if (!CategorySimilarity.isKnownGroup(place.categoryGroup())) {
                throw new IllegalStateException(prefix
                        + "field 'category_group' must be one of culture, nature, attraction, "
                        + "shopping, food, nightlife");
            }

            requireCoordinate(place.latitude(), MIN_LATITUDE, MAX_LATITUDE,
                    prefix + "field 'latitude'");
            requireCoordinate(place.longitude(), MIN_LONGITUDE, MAX_LONGITUDE,
                    prefix + "field 'longitude'");

            String indoorClass = place.indoorClass();
            if (!isBlank(indoorClass) && !INDOOR_CLASSES.contains(indoorClass)) {
                throw new IllegalStateException(prefix
                        + "field 'indoor_class' must be one of INDOOR, OUTDOOR, UNKNOWN");
            }

            String indoorSource = normaliseIndoorSource(place.indoorSource());
            if (indoorSource != null && !INDOOR_SOURCES.contains(indoorSource)) {
                throw new IllegalStateException(prefix
                        + "field 'indoor_source' must be one of OSM_TAG, MANUAL, AI");
            }

            requireRange(place.qualityScore(), prefix + "field 'quality_score'");
            requireRange(place.popularityScore(), prefix + "field 'popularity_score'");
            requireNonNegative(place.estCost(), prefix + "field 'est_cost'");

            String identity = place.osmType() + "/" + place.osmId();
            if (!identities.add(identity)) {
                throw new IllegalStateException(prefix
                        + "duplicates OSM identity '" + identity + "' already present earlier in the seed");
            }
        }
    }

    private static String placePrefix(int index, Place place) {
        String name = place == null || isBlank(place.name()) ? "<unnamed>" : place.name();
        return "Seed place [" + index + "] (" + name + "): ";
    }

    private static void requireText(String value, String label) {
        if (isBlank(value)) {
            throw new IllegalStateException(label + " must not be blank");
        }
    }

    private static void requireCoordinate(Double value, double min, double max, String label) {
        if (value == null) {
            throw new IllegalStateException(label + " must not be null");
        }
        if (value < min || value > max) {
            throw new IllegalStateException(
                    label + " must be between " + min + " and " + max + " (was " + value + ")");
        }
    }

    private static void requireRange(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalStateException(label + " must not be null");
        }
        if (value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
            throw new IllegalStateException(
                    label + " must be between 0 and 1 (was " + value + ")");
        }
    }

    private static void requireNonNegative(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalStateException(label + " must not be null");
        }
        if (value.compareTo(ZERO) < 0) {
            throw new IllegalStateException(label + " must be >= 0 (was " + value + ")");
        }
    }

    private static String normaliseIndoorSource(String source) {
        return isBlank(source) ? null : source.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Counts from one import run. */
    public record SeedResult(int inserted, int skipped, int scoresInserted) {
    }
}
