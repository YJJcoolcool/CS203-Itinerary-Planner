package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smu.cs203_g1_1.itinerary_planner.config.CacheConfig;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.AddressDto;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.BoundingBoxDto;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.geo.nominatim.NominatimAddress;
import com.smu.cs203_g1_1.itinerary_planner.geo.nominatim.NominatimPlace;

/**
 * Cached gateway between controllers and Nominatim. Each read method is memoised under a
 * key derived from its arguments, and raw Nominatim records are mapped to the internal
 * {@link PlaceDto} shape.
 */
@Service
public class GeoService {

    private final NominatimClient nominatimClient;

    /**
     * @param nominatimClient client that performs the upstream Nominatim calls
     */
    public GeoService(NominatimClient nominatimClient) {
        this.nominatimClient = nominatimClient;
    }

    /**
     * Searches places by free text, memoised per normalised query, limit, country codes,
     * view box, and bounded flag.
     *
     * @return matching places, possibly empty
     */
    @Cacheable(cacheNames = CacheConfig.GEOCODE_SEARCH, key = "#query.toLowerCase().trim() + '|' + #limit + '|' + #countryCodes + '|' + #viewBox + '|' + #bounded")
    public List<PlaceDto> search(String query, int limit, String countryCodes, String viewBox, boolean bounded) {
        return nominatimClient.search(query, limit, countryCodes, viewBox, bounded).stream()
                .map(this::toPlaceDto)
                .toList();
    }

    /**
     * Reverse-geocodes coordinates, memoised per lat/lon/zoom.
     *
     * @return the nearest place
     * @throws GeoNotFoundException when Nominatim has no match for the coordinates
     */
    @Cacheable(cacheNames = CacheConfig.GEOCODE_REVERSE, key = "#lat + '|' + #lon + '|' + #zoom")
    public PlaceDto reverse(double lat, double lon, int zoom) {
        return nominatimClient.reverse(lat, lon, zoom)
                .map(this::toPlaceDto)
                .orElseThrow(() -> new GeoNotFoundException("No location found for the given coordinates"));
    }

    /**
     * Looks up one place by OSM identity, memoised per {@code osmType:osmId}.
     *
     * @return the place when OSM knows it, otherwise empty
     */
    @Cacheable(cacheNames = CacheConfig.OSM_LOOKUP, key = "#osmType + ':' + #osmId")
    public Optional<PlaceDto> lookup(String osmType, long osmId) {
        return nominatimClient.lookup(osmType, osmId).map(this::toPlaceDto);
    }

    /**
     * Maps a raw Nominatim record into the internal {@link PlaceDto}; returns null for a null input.
     */
    private PlaceDto toPlaceDto(NominatimPlace place) {
        if (place == null) {
            return null;
        }
        return new PlaceDto(
                place.osmType(),
                place.osmId(),
                place.name(),
                place.displayName(),
                place.category(),
                place.type(),
                parseDouble(place.lat()),
                parseDouble(place.lon()),
                toAddressDto(place.address()),
                openingHours(place),
                toBoundingBoxDto(place.boundingbox()),
                place.importance());
    }

    /**
     * Maps a Nominatim address, collapsing city/town/village into a single city field;
     * returns null for a null input.
     */
    private static AddressDto toAddressDto(NominatimAddress address) {
        if (address == null) {
            return null;
        }
        String city = firstNonBlank(address.city(), address.town(), address.village());
        return new AddressDto(
                address.houseNumber(),
                address.road(),
                city,
                address.state(),
                address.postcode(),
                address.country(),
                address.countryCode());
    }

    /**
     * Extracts {@code opening_hours} from the record's extra tags; null when absent.
     */
    private static String openingHours(NominatimPlace place) {
        Map<String, String> extratags = place.extratags();
        if (extratags == null) {
            return null;
        }
        return extratags.get("opening_hours");
    }

    /**
     * Converts Nominatim's {@code boundingbox} order [southLat, northLat, westLon, eastLon]
     * into a {@link BoundingBoxDto}; null unless exactly four values are present.
     */
    private static BoundingBoxDto toBoundingBoxDto(List<String> boundingbox) {
        if (boundingbox == null || boundingbox.size() != 4) {
            return null;
        }
        double south = parseDouble(boundingbox.get(0));
        double north = parseDouble(boundingbox.get(1));
        double west = parseDouble(boundingbox.get(2));
        double east = parseDouble(boundingbox.get(3));
        return new BoundingBoxDto(south, west, north, east);
    }

    /**
     * Parses a decimal string, defaulting to 0.0 for null, blank, or unparseable input.
     */
    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0d;
        }
    }

    /**
     * Returns the first non-blank value, or null when none qualify.
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
