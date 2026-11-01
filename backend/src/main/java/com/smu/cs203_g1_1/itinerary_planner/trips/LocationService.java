package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** What a user may pick for a manual place (CGT-141); "not sure" is sent as null. */
    private static final Set<String> MANUAL_INDOOR_CLASSES = Set.of("INDOOR", "OUTDOOR");

    private final LocationRepository locationRepository;
    private final GeoService geoService;
    private final TripService tripService;
    private final IndoorClassifier indoorClassifier = new IndoorClassifier();

    /**
     * @param locationRepository persistence for stored locations
     * @param geoService         hydrates new locations from OSM when a row is not already stored
     * @param tripService        trip membership and role checks for trip-private places
     */
    public LocationService(LocationRepository locationRepository, GeoService geoService, TripService tripService) {
        this.locationRepository = locationRepository;
        this.geoService = geoService;
        this.tripService = tripService;
    }

    /** Shared places only (seeded or OpenStreetMap); trip-private places never appear here. */
    public List<Location> getSharedLocations() {
        return locationRepository.findTrusted();
    }

    /**
     * Returns a location the user may see (CGT-142): a shared place, or a place
     * private to a trip they are a member of (403 otherwise). Old manual places
     * that belong to no trip are hidden (404).
     */
    @Transactional(readOnly = true)
    public Location getVisibleLocation(String id, String userId) {
        Location location = getLocationById(id);

        if (location.getTrip() != null) {
            tripService.getTripForMember(location.getTrip().getId(), userId);
        } else if (location.isManual()) {
            log.warn("Hidden unowned manual location requested: id={}", id);
            throw new LocationNotFoundException(id);
        }

        return location;
    }

    /**
     * Creates a place the user typed in, private to {@code tripId} (CGT-142).
     * Requires EDITOR or above on that trip. The optional indoor/outdoor choice
     * (CGT-141) lets rain checks treat the place like any classified one.
     */
    @Transactional
    public Location createManualLocation(String tripId, String userId, ManualLocationRequest request) {
        Trip trip = tripService.requireTripRole(tripId, userId, TripRole.EDITOR);
        String indoorClass = manualIndoorClass(request.indoorClass());

        Location location = new Location(
                request.name() == null ? null : request.name().trim(),
                trimToNull(request.category()),
                request.latitude(),
                request.longitude(),
                legacyIndoorFlag(indoorClass));
        location.setTrip(trip);
        location.setCountryCode(trip.getCountryCode());

        if (indoorClass != null) {
            location.setIndoorClass(indoorClass);
            location.setIndoorSource("MANUAL");
            location.setIndoorConfidence(1.0);
        }

        Location saved = createLocation(location);
        log.info("Created manual location id={} for trip id={} (indoor={})", saved.getId(), tripId, indoorClass);
        return saved;
    }

    /** INDOOR or OUTDOOR, or null for blank/"not sure"; anything else is a 400. */
    private static String manualIndoorClass(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!MANUAL_INDOOR_CLASSES.contains(upper)) {
            throw new IllegalArgumentException("indoorClass must be INDOOR, OUTDOOR, or empty");
        }
        return upper;
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

    /**
     * Deletes a trip-private place (CGT-142). Requires EDITOR or above on its
     * trip. Shared places (and old unowned manual ones) can't be deleted, since
     * other trips may rely on them; a place still used by an activity is a 409.
     */
    @Transactional
    public void deleteLocation(String id, String userId) {
        Location location = getLocationById(id);

        if (location.getTrip() == null) {
            log.warn("Rejected delete of shared location id={} by user id={}", id, userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only places added to a trip can be deleted");
        }

        tripService.requireTripRole(location.getTrip().getId(), userId, TripRole.EDITOR);

        if (!location.getActivities().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This place is still used by an activity; remove it from the activity first");
        }

        locationRepository.delete(location);
        log.info("Deleted location id={}", id);
    }

    /** CGT-142: a trip-private place can only be used in its own trip's activities. */
    public void requireUsableInTrip(Location location, String tripId) {
        if (location.getTrip() != null && !location.getTrip().getId().equals(tripId)) {
            log.warn("Rejected use of location id={} from another trip in trip id={}", location.getId(), tripId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "That place belongs to another trip");
        }
    }
}
