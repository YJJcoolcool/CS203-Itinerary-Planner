package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smu.cs203_g1_1.itinerary_planner.geo.GeoNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoService;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.planner.CategoryGroups;
import com.smu.cs203_g1_1.itinerary_planner.seed.IndoorClassifier;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

/**
 * Manages stored locations, including idempotent import of OpenStreetMap places by identity.
 */
@Service
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository locationRepository;
    private final GeoService geoService;
    private final IndoorClassifier indoorClassifier = new IndoorClassifier();

    /**
     * @param locationRepository persistence for stored locations
     * @param geoService         hydrates new locations from OSM when a row is not already stored
     */
    public LocationService(LocationRepository locationRepository, GeoService geoService) {
        this.locationRepository = locationRepository;
        this.geoService = geoService;
    }

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    public Location getLocationById(String id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Location not found: id={}", id);
                    return new LocationNotFoundException(id);
                });
    }

    public Location createLocation(Location location) {
        // Validate name
        if (location.getName() == null || location.getName().isBlank()) {
            throw new IllegalArgumentException("Location name must not be blank");
        }

        // Validate coordinates
        if (location.getLatitude() == null
                || location.getLatitude() < -90
                || location.getLatitude() > 90) {
            throw new IllegalArgumentException(
                    "Latitude must be between -90 and 90"
            );
        }

        if (location.getLongitude() == null
                || location.getLongitude() < -180
                || location.getLongitude() > 180) {
            throw new IllegalArgumentException(
                    "Longitude must be between -180 and 180"
            );
        }

        // Trust fields are server-owned: a client-made place must never pass as curated or OSM.
        location.setSeeded(false);
        location.setOsmType(null);
        location.setOsmId(null);

        // Generate ID and save
        location.setId(ID.generateUniqueID(locationRepository::existsById));
        Location saved = locationRepository.save(location);

        // Keep your existing logging here, if any
        return saved;
    }

    /**
     * Returns the location for an OSM identity, importing it on first use. Idempotent by
     * {@code (osmType, osmId)}; new rows are hydrated from {@link GeoService#lookup} and
     * saved in a transaction.
     *
     * @throws GeoNotFoundException when OSM has no record for the given identity
     */
    @Transactional
    public Location findOrCreateFromOsm(String osmType, long osmId) {
        return locationRepository.findByOsmTypeAndOsmId(osmType, osmId)
                .orElseGet(() -> {
                    PlaceDto place = geoService.lookup(osmType, osmId)
                            .orElseThrow(() -> new GeoNotFoundException("No OSM place found for " + osmType + "/" + osmId));

                    IndoorClassifier.IndoorClassification indoor =
                            indoorClassifier.classify(place.type(), osmTags(place));

                    Location location = new Location();
                    location.setId(ID.generateUniqueID(locationRepository::existsById));
                    location.setName(firstNonBlank(place.name(), place.displayName(), osmType + " " + osmId));
                    location.setCategory(place.type() != null ? place.type() : place.category());
                    location.setLatitude(place.latitude());
                    location.setLongitude(place.longitude());
                    location.setOsmType(place.osmType() != null ? place.osmType() : osmType);
                    location.setOsmId(place.osmId() != null ? place.osmId() : osmId);
                    location.setAddress(place.displayName());
                    location.setCountryCode(place.address() == null
                            ? null
                            : place.address().countryCode());
                    location.setOpeningHours(place.openingHours());
                    location.setCategoryGroup(CategoryGroups.from(place.category(), place.type()));
                    location.setIndoorClass(indoor.indoorClass());
                    location.setIndoorSource(indoor.source());
                    location.setIndoorConfidence(indoor.confidence());
                    location.setIsIndoor(legacyIndoorFlag(indoor.indoorClass()));

                    Location saved = locationRepository.save(location);
                    log.info("Imported OSM location id={} osm={}/{}", saved.getId(), saved.getOsmType(), saved.getOsmId());
                    return saved;
                });
    }

    /** Returns the first non-blank value, or null when none qualify. */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Builds the one-entry OSM tag map used by {@link IndoorClassifier}. When
     * the category is missing, the key is inferred from the value so a
     * type-only place can still be classified.
     */
    private static Map<String, String> osmTags(PlaceDto place) {
        String category = trimToNull(place.category());
        String type = trimToNull(place.type());

        if (category != null && type != null) {
            return Map.of(category, type);
        }
        if (type == null) {
            return Map.of();
        }

        String inferredKey = CategoryGroups.inferKey(type);
        return inferredKey == null ? Map.of() : Map.of(inferredKey, type);
    }

    /** Mirrors the legacy {@code isIndoor} flag, leaving it null when unknown. */
    private static Boolean legacyIndoorFlag(String indoorClass) {
        if ("INDOOR".equals(indoorClass)) {
            return true;
        }
        if ("OUTDOOR".equals(indoorClass)) {
            return false;
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean deleteLocation(String id) {
        if (locationRepository.existsById(id)) {
            locationRepository.deleteById(id);
            log.info("Deleted location id={}", id);
            return true;
        }

        log.warn("Location not found for deletion: id={}", id);
        return false;
    }
}
