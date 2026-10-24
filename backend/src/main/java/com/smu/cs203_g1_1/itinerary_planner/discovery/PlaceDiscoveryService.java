package com.smu.cs203_g1_1.itinerary_planner.discovery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.smu.cs203_g1_1.itinerary_planner.rag.PlaceEmbedding;
import com.smu.cs203_g1_1.itinerary_planner.rag.PlaceEmbeddingRepository;
import com.smu.cs203_g1_1.itinerary_planner.rag.SemanticRanker;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Finds the places that best match the embedded trip context.
 *
 * <p>This is the semantic arm of discovery. It deliberately does not filter:
 * it returns ids, and {@code CandidateFinder} still applies every hard rule
 * (distance, indoor requirement, opening hours, exclusions). A semantic hit can
 * raise a place's score, never bypass a constraint.</p>
 *
 * <p>The bbox query keeps the candidate set to a few hundred rows, which is why
 * cosine ranking happens in Java rather than in a vector database.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class PlaceDiscoveryService {

    private static final double KM_PER_DEGREE_LAT = 111.0;
    private static final double MIN_COS_LAT = 0.01;

    private final LocationRepository locations;
    private final PlaceEmbeddingRepository embeddings;

    public PlaceDiscoveryService(LocationRepository locations,
                                 PlaceEmbeddingRepository embeddings) {
        this.locations = locations;
        this.embeddings = embeddings;
    }

    /**
     * Returns up to {@code topK} location ids ordered by semantic similarity.
     * Empty when there is no query vector or nothing is embedded nearby.
     */
    public Set<String> preferredIds(double latitude,
                                    double longitude,
                                    double radiusKm,
                                    float[] queryVector,
                                    int topK) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return Set.of();
        }

        double latDelta = radiusKm / KM_PER_DEGREE_LAT;
        double cosLat = Math.cos(Math.toRadians(latitude));
        double lonDelta = Math.abs(cosLat) < MIN_COS_LAT
            ? 180.0
            : radiusKm / (KM_PER_DEGREE_LAT * Math.abs(cosLat));

        List<Location> nearby = locations.findTrustedInBoundingBox(
            latitude - latDelta,
            latitude + latDelta,
            longitude - lonDelta,
            longitude + lonDelta);

        if (nearby.isEmpty()) {
            return Set.of();
        }

        List<String> ids = nearby.stream()
            .map(Location::getId)
            .filter(Objects::nonNull)
            .toList();

        if (ids.isEmpty()) {
            return Set.of();
        }

        List<PlaceEmbedding> embedded = embeddings.findByLocationIdIn(ids);

        return SemanticRanker.rank(queryVector, embedded, topK).stream()
            .map(SemanticRanker.Scored::locationId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
