package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smu.cs203_g1_1.itinerary_planner.config.CacheConfig;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.AddressDto;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.geo.overpass.OverpassCenter;
import com.smu.cs203_g1_1.itinerary_planner.geo.overpass.OverpassElement;

/**
 * Discovers nearby points of interest through Overpass. Only whitelisted categories are
 * accepted so user input cannot be injected into the generated Overpass QL.
 */
@Service
public class PoiService {

    private static final List<String> CATEGORY_TAG_KEYS = List.of("amenity", "tourism", "shop", "leisure");

    private static final Map<String, TagFilter> WHITELIST = Map.of(
            "restaurant", new TagFilter("amenity", "restaurant"),
            "cafe", new TagFilter("amenity", "cafe"),
            "bar", new TagFilter("amenity", "bar"),
            "fast_food", new TagFilter("amenity", "fast_food"),
            "hotel", new TagFilter("tourism", "hotel"),
            "hostel", new TagFilter("tourism", "hostel"),
            "attraction", new TagFilter("tourism", "attraction"),
            "museum", new TagFilter("tourism", "museum"));

    private final OverpassClient overpassClient;

    /**
     * @param overpassClient client that runs the Overpass QL queries
     */
    public PoiService(OverpassClient overpassClient) {
        this.overpassClient = overpassClient;
    }

    /**
     * Finds up to {@code limit} whitelisted POIs around a coordinate. Results are cached by
     * the query parameters, and the Overpass QL is built solely from whitelisted tag pairs.
     *
     * @param lat      latitude
     * @param lon      longitude
     * @param radius   search radius in metres
     * @param category whitelisted category, e.g. restaurant, cafe, museum
     * @param limit    maximum number of results to return
     * @return matching places, capped at {@code limit}
     * @throws GeoBadRequestException if {@code category} is not whitelisted
     */
    @Cacheable(cacheNames = CacheConfig.POIS, key = "#lat + '|' + #lon + '|' + #radius + '|' + #category.toLowerCase() + '|' + #limit")
    public List<PlaceDto> findPois(double lat, double lon, int radius, String category, int limit) {
        TagFilter filter = category == null ? null : WHITELIST.get(category.toLowerCase());
        if (filter == null) {
            throw new GeoBadRequestException("Unsupported category: " + category);
        }
        String overpassQl = "[out:json][timeout:25];\n"
                + "nwr[\"" + filter.key() + "\"=\"" + filter.value() + "\"](around:" + radius + "," + lat + "," + lon + ");\n"
                + "out center;";
        List<PlaceDto> results = new ArrayList<>();
        for (OverpassElement element : overpassClient.query(overpassQl)) {
            PlaceDto dto = toPlaceDto(element);
            if (dto != null) {
                results.add(dto);
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        return results;
    }

    /**
     * Maps an Overpass element to a {@link PlaceDto}, preferring explicit coordinates and
     * falling back to the element {@code center} produced by {@code out center}. Returns
     * null for elements without tags, a name, or usable coordinates.
     */
    private static PlaceDto toPlaceDto(OverpassElement element) {
        if (element == null) {
            return null;
        }
        Map<String, String> tags = element.tags();
        if (tags == null) {
            return null;
        }
        String name = tags.get("name");
        if (name == null || name.isBlank()) {
            return null;
        }

        Double lat = element.lat();
        Double lon = element.lon();
        if (lat == null || lon == null) {
            OverpassCenter center = element.center();
            if (center == null || center.lat() == null || center.lon() == null) {
                return null;
            }
            lat = center.lat();
            lon = center.lon();
        }

        String categoryKey = null;
        String categoryValue = null;
        for (String key : CATEGORY_TAG_KEYS) {
            String value = tags.get(key);
            if (value != null && !value.isBlank()) {
                categoryKey = key;
                categoryValue = value;
                break;
            }
        }

        String houseNumber = tags.get("addr:housenumber");
        String road = tags.get("addr:street");
        String city = tags.get("addr:city");
        String postcode = tags.get("addr:postcode");
        String country = tags.get("addr:country");
        String countryCode = tags.get("addr:country_code");

        AddressDto address = new AddressDto(
                houseNumber,
                road,
                city,
                null,
                postcode,
                country,
                countryCode);

        String displayName = joinNonBlank(name, road, city, country);

        return new PlaceDto(
                element.type(),
                element.id(),
                name,
                displayName,
                categoryKey,
                categoryValue,
                lat,
                lon,
                address,
                tags.get("opening_hours"),
                null,
                null);
    }

    /**
     * Joins the non-blank values with ", ".
     *
     * @return the joined text, or null when every value is blank
     */
    private static String joinNonBlank(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(value.trim());
            }
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /** Whitelisted OSM tag key/value pair used to build a safe Overpass filter. */
    private record TagFilter(String key, String value) {
    }
}
