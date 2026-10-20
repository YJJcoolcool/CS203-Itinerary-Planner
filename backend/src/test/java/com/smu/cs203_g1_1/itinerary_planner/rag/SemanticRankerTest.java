package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SemanticRankerTest {

    private static final float[] QUERY = {1f, 0f};

    @Test
    void ordersByCosineSimilarity() {
        Map<String, float[]> vectors = new LinkedHashMap<>();
        vectors.put("north", new float[] {0f, 1f});
        vectors.put("exact", new float[] {1f, 0f});
        vectors.put("close", new float[] {0.9f, 0.1f});

        List<SemanticRanker.Scored> ranked = SemanticRanker.rank(QUERY, vectors, 3);

        assertEquals(List.of("exact", "close", "north"),
            ranked.stream().map(SemanticRanker.Scored::locationId).toList());
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
    }

    @Test
    void respectsTopK() {
        Map<String, float[]> vectors = Map.of(
            "a", new float[] {1f, 0f},
            "b", new float[] {0.8f, 0.2f},
            "c", new float[] {0f, 1f});

        assertEquals(2, SemanticRanker.rank(QUERY, vectors, 2).size());
    }

    @Test
    void skipsVectorsWithTheWrongDimension() {
        Map<String, float[]> vectors = Map.of(
            "good", new float[] {1f, 0f},
            "wrong", new float[] {1f, 0f, 0f});

        List<SemanticRanker.Scored> ranked = SemanticRanker.rank(QUERY, vectors, 5);

        assertEquals(1, ranked.size());
        assertEquals("good", ranked.get(0).locationId());
    }

    @Test
    void returnsNothingForEmptyInputs() {
        assertTrue(SemanticRanker.rank(QUERY, Map.of(), 5).isEmpty());
        assertTrue(SemanticRanker.rank(null, Map.of("a", QUERY), 5).isEmpty());
        assertTrue(SemanticRanker.rank(QUERY, Map.of(), 0).isEmpty());
    }

    @Test
    void zeroVectorsScoreZeroRatherThanNaN() {
        Map<String, float[]> vectors = Map.of("zero", new float[] {0f, 0f});

        assertEquals(0.0, SemanticRanker.rank(QUERY, vectors, 1).get(0).score());
    }

    @Test
    void ranksStoredEmbeddings() {
        List<PlaceEmbedding> embeddings = List.of(
            new PlaceEmbedding("place001", "test-model", new float[] {1f, 0f}),
            new PlaceEmbedding("place002", "test-model", new float[] {0f, 1f}));

        List<SemanticRanker.Scored> ranked = SemanticRanker.rank(QUERY, embeddings, 2);

        assertEquals("place001", ranked.get(0).locationId());
        assertEquals(1.0, ranked.get(0).score(), 1e-6);
    }
}
