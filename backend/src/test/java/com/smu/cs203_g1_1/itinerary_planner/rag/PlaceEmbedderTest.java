package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.List;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class PlaceEmbedderTest {

    @Mock
    private LocationRepository locations;

    @Mock
    private PlaceEmbeddingRepository embeddings;

    @Mock
    private EmbeddingModel embeddingModel;

    private PlaceEmbedder embedder;

    @BeforeEach
    void setUp() {
        embedder = new PlaceEmbedder(locations, embeddings, embeddingModel, 2);
    }

    @Test
    void embedsOnlyPlacesWithoutAVector() {
        when(locations.findAll()).thenReturn(List.of(
            location("loc00001", "Museum"),
            location("loc00002", "Park")));
        when(embeddings.findAll()).thenReturn(List.of(
            new PlaceEmbedding("loc00001", "test-model", new float[] {1f, 0f})));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {0f, 1f}));

        PlaceEmbedder.EmbedSummary summary = embedder.embedMissing("test-model");

        assertEquals(1, summary.candidates());
        assertEquals(1, summary.embedded());
        assertEquals(0, summary.failedBatches());
        verify(embeddings).saveAll(any());
    }

    @Test
    void doesNothingWhenEverythingIsAlreadyEmbedded() {
        when(locations.findAll()).thenReturn(List.of(location("loc00001", "Museum")));
        when(embeddings.findAll()).thenReturn(List.of(
            new PlaceEmbedding("loc00001", "test-model", new float[] {1f, 0f})));

        PlaceEmbedder.EmbedSummary summary = embedder.embedMissing("test-model");

        assertEquals(0, summary.candidates());
        assertEquals(0, summary.embedded());
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void aFailedBatchIsCountedAndDoesNotAbortTheRun() {
        when(locations.findAll()).thenReturn(List.of(location("loc00001", "Museum")));
        when(embeddings.findAll()).thenReturn(List.of());
        when(embeddingModel.embed(anyList())).thenThrow(new IllegalStateException("provider down"));

        PlaceEmbedder.EmbedSummary summary = embedder.embedMissing("test-model");

        assertEquals(1, summary.failedBatches());
        assertEquals(0, summary.embedded());
        verify(embeddings, never()).saveAll(any());
    }

    @Test
    void aVectorCountMismatchIsTreatedAsAFailedBatch() {
        when(locations.findAll()).thenReturn(List.of(
            location("loc00001", "Museum"),
            location("loc00002", "Park")));
        when(embeddings.findAll()).thenReturn(List.of());
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {1f, 0f}));

        PlaceEmbedder.EmbedSummary summary = embedder.embedMissing("test-model");

        assertEquals(1, summary.failedBatches());
        assertEquals(0, summary.embedded());
    }

    @Test
    void reEmbedsRowsWrittenByADifferentModel() {
        when(locations.findAll()).thenReturn(List.of(location("loc00001", "Museum")));
        when(embeddings.findAll()).thenReturn(List.of(
            new PlaceEmbedding("loc00001", "old-model", new float[] {1f, 0f})));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {1f, 0f}));

        PlaceEmbedder.EmbedSummary summary = embedder.embedMissing("test-model");

        assertEquals(1, summary.candidates());
        assertEquals(1, summary.embedded());
        verify(embeddings).saveAll(any());
    }

    @Test
    void reEmbedsRowsWrittenWithDifferentDimensions() {
        when(locations.findAll()).thenReturn(List.of(location("loc00001", "Museum")));
        when(embeddings.findAll()).thenReturn(List.of(
            new PlaceEmbedding("loc00001", "test-model", new float[] {1f, 0f, 0f})));
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {1f, 0f}));

        PlaceEmbedder.EmbedSummary summary = embedder.embedMissing("test-model");

        assertEquals(1, summary.candidates());
        assertEquals(1, summary.embedded());
        verify(embeddings).saveAll(any());
    }

    @Test
    void rejectsVectorsOverTheByteCap() {
        when(locations.findAll()).thenReturn(List.of(location("loc00001", "Museum")));
        when(embeddings.findAll()).thenReturn(List.of());
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[2049]));

        PlaceEmbedder oversized =
            new PlaceEmbedder(locations, embeddings, embeddingModel, 2049);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> oversized.embedMissing("test-model"));

        assertTrue(ex.getMessage().contains("test-model"));
        assertTrue(ex.getMessage().contains("2049"));
        verify(embeddings, never()).saveAll(any());
    }

    private static Location location(String id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        location.setCategory("museum");
        location.setCategoryGroup("culture");
        location.setIndoorClass("INDOOR");

        return location;
    }
}
