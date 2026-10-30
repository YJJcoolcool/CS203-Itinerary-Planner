package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.planner.CategoryGroups;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Imports a country OSM POI extract as non-curated locations.
 *
 * <p>Reads the GeoJSON emitted by {@code scripts/build-sg-pois.sh} and maps each
 * feature through the same rules the curated seed uses: {@link CategoryGroups}
 * for the interest group and {@link IndoorClassifier} for indoor evidence. The
 * curated places keep {@code seeded=true} and are never overwritten; index
 * rows are written with {@code seeded=false} and rank below them.</p>
 *
 * <p>Idempotent by {@code (osm_type, osm_id)}. Features without a name, a known
 * category, or a usable point inside the configured region's bounding boxes are
 * skipped and counted.</p>
 */
@Component
public class OsmIndexImporter {

    private static final Logger log = LoggerFactory.getLogger(OsmIndexImporter.class);

    /** Tag keys checked in priority order; the first mapped pair wins. */
    private static final List<String> CATEGORY_KEYS =
        List.of("tourism", "leisure", "shop", "amenity", "historic");

    private static final int SAVE_BATCH_SIZE = 500;

    private final LocationRepository locations;
    private final RegionProfile region;

    @Autowired
    public OsmIndexImporter(LocationRepository locations,
                            RegionRegistry regionRegistry,
                            @Value("${app.seed.country:sg}") String seedCountry) {
        this.locations = locations;
        this.region = regionRegistry.byCode(seedCountry)
            .orElseThrow(() -> new IllegalStateException(
                "Unknown app.seed.country: " + seedCountry));
    }

    /** Test seam: imports with the default SG region. */
    OsmIndexImporter(LocationRepository locations, RegionRegistry regionRegistry) {
        this(locations, regionRegistry, "sg");
    }

    /** Imports the GeoJSON at {@code path}. */
    public ImportSummary importFrom(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return importFrom(input);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to read POI index: " + path, ex);
        }
    }

    /** Imports GeoJSON from a stream (used by tests). */
    public ImportSummary importFrom(InputStream input) {
        JsonNode root;

        try {
            root = new ObjectMapper().readTree(input);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to parse POI index", ex);
        }

        if (root == null) {
            throw new IllegalArgumentException("Empty POI index");
        }

        return importFrom(root);
    }

    /** Imports an already-parsed GeoJSON FeatureCollection. */
    public ImportSummary importFrom(JsonNode root) {
        JsonNode features = root.path("features");

        if (!features.isArray()) {
            throw new IllegalArgumentException("POI index has no features array");
        }

        Set<String> existing = new HashSet<>();
        Set<String> usedIds = new HashSet<>();

        for (Location location : locations.findAll()) {
            existing.add(key(location.getOsmType(), location.getOsmId()));

            if (location.getId() != null) {
                usedIds.add(location.getId());
            }
        }

        int total = 0;
        int imported = 0;
        int unnamed = 0;
        int unknownCategory = 0;
        int outsideRegion = 0;
        int noGeometry = 0;
        int duplicate = 0;

        List<Location> batch = new ArrayList<>(SAVE_BATCH_SIZE);

        for (JsonNode feature : features) {
            total++;

            JsonNode properties = feature.path("properties");
            String name = text(properties, "name");

            if (name == null) {
                unnamed++;
                continue;
            }

            Map<String, String> tags = tags(properties);
            MappedCategory category = category(tags);

            if (category == null) {
                unknownCategory++;
                continue;
            }

            String osmType = text(properties, "@type");
            long osmId = properties.path("@id").asLong(0);

            if (osmType == null || osmId <= 0) {
                unknownCategory++;
                continue;
            }

            double[] coordinates = centroid(feature.path("geometry"));

            if (coordinates == null) {
                noGeometry++;
                continue;
            }

            double latitude = coordinates[0];
            double longitude = coordinates[1];

            if (!contains(latitude, longitude)) {
                outsideRegion++;
                continue;
            }

            if (!existing.add(key(osmType, osmId))) {
                duplicate++;
                continue;
            }

            batch.add(toLocation(nextId(usedIds), name, category, osmType, osmId,
                latitude, longitude, tags));

            if (batch.size() >= SAVE_BATCH_SIZE) {
                locations.saveAll(batch);
                imported += batch.size();
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            locations.saveAll(batch);
            imported += batch.size();
        }

        ImportSummary summary = new ImportSummary(
            total, imported, unnamed, unknownCategory,
            outsideRegion, noGeometry, duplicate);

        log.info("OSM index import: {}", summary.describe());

        return summary;
    }

    private Location toLocation(String id,
                                String name,
                                MappedCategory category,
                                String osmType,
                                long osmId,
                                double latitude,
                                double longitude,
                                Map<String, String> tags) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        location.setCategory(category.value());
        location.setCategoryGroup(category.group());
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAddress(address(tags, region.name()));
        location.setCountryCode(region.code());
        location.setOpeningHours(tags.get("opening_hours"));
        location.setOsmType(osmType);
        location.setOsmId(osmId);
        location.setSeeded(false);
        location.setEstCost((BigDecimal) null);

        IndoorClassifier.IndoorClassification indoor =
            new IndoorClassifier().classify(category.value(), tags);

        location.setIndoorClass(indoor.indoorClass());
        location.setIndoorSource(indoor.source());
        location.setIndoorConfidence(indoor.confidence());

        return location;
    }

    private static MappedCategory category(Map<String, String> tags) {
        for (String key : CATEGORY_KEYS) {
            String value = tags.get(key);

            if (value == null) {
                continue;
            }

            String group = CategoryGroups.from(key, value);

            if (group != null) {
                return new MappedCategory(value, group);
            }
        }

        return null;
    }

    private static Map<String, String> tags(JsonNode properties) {
        Map<String, String> tags = new HashMap<>();

        properties.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();

            if (value != null && value.isValueNode()) {
                tags.put(entry.getKey(), value.asText());
            }
        });

        return tags;
    }

    private static String address(Map<String, String> tags, String countryName) {
        String houseNumber = blankToNull(tags.get("addr:housenumber"));
        String street = blankToNull(tags.get("addr:street"));
        String postcode = blankToNull(tags.get("addr:postcode"));

        StringBuilder address = new StringBuilder();

        if (houseNumber != null) {
            address.append(houseNumber);
        }

        if (street != null) {
            if (!address.isEmpty()) {
                address.append(' ');
            }
            address.append(street);
        }

        if (postcode != null) {
            if (!address.isEmpty()) {
                address.append(", ");
            }
            address.append(countryName).append(' ').append(postcode);
        }

        return address.isEmpty() ? null : address.toString();
    }

    /** Returns {latitude, longitude} for a Point, LineString, Polygon or MultiPolygon. */
    static double[] centroid(JsonNode geometry) {
        if (geometry == null || geometry.isMissingNode()) {
            return null;
        }

        String type = geometry.path("type").asText("");
        JsonNode coordinates = geometry.path("coordinates");

        return switch (type) {
            case "Point" -> point(coordinates);
            case "LineString", "Polygon" -> ring(coordinates.path(0));
            case "MultiLineString", "MultiPolygon" -> ring(coordinates.path(0).path(0));
            default -> null;
        };
    }

    private static double[] point(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }

        return new double[] {coordinates.get(1).asDouble(), coordinates.get(0).asDouble()};
    }

    private static double[] ring(JsonNode ring) {
        if (ring == null || !ring.isArray() || ring.isEmpty()) {
            return null;
        }

        double latitude = 0;
        double longitude = 0;
        int count = 0;

        for (JsonNode point : ring) {
            if (point.isArray() && point.size() >= 2) {
                longitude += point.get(0).asDouble();
                latitude += point.get(1).asDouble();
                count++;
            }
        }

        return count == 0
            ? null
            : new double[] {latitude / count, longitude / count};
    }

    private boolean contains(double latitude, double longitude) {
        return region.bboxes().stream()
            .anyMatch(bbox -> bbox.contains(latitude, longitude));
    }

    private static String key(String osmType, Long osmId) {
        return osmType + ":" + osmId;
    }

    /**
     * Generates an unused 8-character id from the in-memory set.
     *
     * <p>Never queries the database: one round trip per place made a remote
     * import take roughly an hour over the tunnel.</p>
     */
    private static String nextId(Set<String> used) {
        String id;

        do {
            id = ID.generateID();
        } while (!used.add(id));

        return id;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);

        return value.isValueNode() ? blankToNull(value.asText()) : null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private record MappedCategory(String value, String group) {}

    /** Counts from one import run; {@link #describe()} is what the runner logs. */
    public record ImportSummary(int total,
                                int imported,
                                int unnamed,
                                int unknownCategory,
                                int outsideRegion,
                                int noGeometry,
                                int duplicate) {

        public String describe() {
            return "total=" + total
                + " imported=" + imported
                + " unnamed=" + unnamed
                + " unknownCategory=" + unknownCategory
                + " outsideRegion=" + outsideRegion
                + " noGeometry=" + noGeometry
                + " duplicate=" + duplicate;
        }
    }
}
