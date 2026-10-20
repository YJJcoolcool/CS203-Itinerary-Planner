package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.ai.embedding.EmbeddingModel;

class QueryEmbedderTest {

    private final EmbeddingModel model = mock(EmbeddingModel.class);

    @Test
    void returnsTheEmbeddedVector() {
        when(model.embed("museums")).thenReturn(new float[] {1f, 0f});

        Optional<float[]> vector = new QueryEmbedder(model).embed("museums");

        assertTrue(vector.isPresent());
        assertEquals(1f, vector.get()[0]);
    }

    @Test
    void cachesRepeatedQueries() {
        when(model.embed("museums")).thenReturn(new float[] {1f, 0f});

        QueryEmbedder embedder = new QueryEmbedder(model);
        embedder.embed("museums");
        embedder.embed("museums");

        verify(model, times(1)).embed("museums");
    }

    @Test
    void failureDegradesToEmptyRatherThanThrowing() {
        when(model.embed(anyString())).thenThrow(new IllegalStateException("provider down"));

        assertTrue(new QueryEmbedder(model).embed("museums").isEmpty());
    }

    @Test
    void blankTextNeverCallsTheProvider() {
        assertTrue(new QueryEmbedder(model).embed("   ").isEmpty());
        verifyNoInteractions(model);
    }

    @Test
    void buildsTheQueryTextFromContext() {
        when(model.embed(anyString())).thenReturn(new float[] {0.5f, 0.5f});

        QueryEmbedder embedder = new QueryEmbedder(model);
        Optional<float[]> vector = embedder.embedQuery(
            "museum", "quiet", Set.of("art"), "rain");

        assertTrue(vector.isPresent());
        verify(model).embed(org.mockito.ArgumentMatchers.contains("Trip goal: museum"));
    }
}
