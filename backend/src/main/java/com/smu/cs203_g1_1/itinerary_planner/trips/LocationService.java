package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

@Service
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
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
        String id = ID.generateUniqueID(locationRepository::existsById);
        location.setId(id);
        
        Location saved = locationRepository.save(location);

        log.info("Created location id={} name={}", saved.getId(), saved.getName());
        return saved;
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
