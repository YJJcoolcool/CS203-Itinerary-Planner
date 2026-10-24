package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cosine ranking over a small candidate set.
 *
 * <p>Pure and deterministic: given the same vectors it always returns the same
 * order. Callers pass only the rows inside the trip's bounding box, so this
 * stays a few hundred comparisons rather than a full scan of the index. That is
 * why there is no vector database here.</p>
 */
public final class SemanticRanker {

    private static final Logger log = LoggerFactory.getLogger(SemanticRanker.class);

    /** A candidate id and its cosine similarity to the query (range -1..1). */
    public record Scored(String locationId, double score) {}

    private SemanticRanker() {
    }

    public static List<Scored> rank(float[] query,
                                    Map<String, float[]> vectors,
                                    int topK) {
        if (query == null || query.length == 0 || vectors == null || vectors.isEmpty() || topK <= 0) {
            return List.of();
        }

        return vectors.entrySet().stream()
            .filter(entry -> usable(entry.getValue(), query.length))
            .map(entry -> new Scored(entry.getKey(), cosine(query, entry.getValue())))
            .sorted(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(Scored::locationId))
            .limit(topK)
            .toList();
    }

    public static List<Scored> rank(float[] query,
                                    List<PlaceEmbedding> embeddings,
                                    int topK) {
        if (query == null || query.length == 0
                || embeddings == null || embeddings.isEmpty() || topK <= 0) {
            return List.of();
        }

        Map<String, float[]> vectors = new LinkedHashMap<>();

        for (PlaceEmbedding embedding : embeddings) {
            if (embedding == null || embedding.getLocationId() == null) {
                continue;
            }

            try {
                float[] vector = embedding.asFloats();

                if (vector != null && vector.length > 0) {
                    vectors.putIfAbsent(embedding.getLocationId(), vector);
                }
            } catch (RuntimeException ex) {
                log.warn("Skipping corrupt embedding for locationId={}: {}",
                    embedding.getLocationId(), ex.getMessage());
            }
        }

        return rank(query, vectors, topK);
    }

    /** Cosine similarity; 0 when either vector has no magnitude. */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static boolean usable(float[] vector, int expectedLength) {
        if (vector == null || vector.length != expectedLength) {
            return false;
        }

        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }

        return true;
    }
}
