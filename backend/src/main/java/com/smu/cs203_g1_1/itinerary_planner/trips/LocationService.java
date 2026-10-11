package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smu.cs203_g1_1.itinerary_planner.geo.GeoNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoService;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

/**
 * Manages stored locations, including idempotent import of OpenStreetMap places by identity.
 */
@Service
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository locationRepository;
    private final GeoService geoService;

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

                    Location location = new Location();
                    location.setId(ID.generateUniqueID(locationRepository::existsById));
                    location.setName(firstNonBlank(place.name(), place.displayName(), osmType + " " + osmId));
                    location.setCategory(place.type() != null ? place.type() : place.category());
                    location.setLatitude(place.latitude());
                    location.setLongitude(place.longitude());
                    location.setOsmType(place.osmType() != null ? place.osmType() : osmType);
                    location.setOsmId(place.osmId() != null ? place.osmId() : osmId);
                    location.setAddress(place.displayName());
                    location.setOpeningHours(place.openingHours());

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
