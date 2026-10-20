package com.smu.cs203_g1_1.itinerary_planner.discovery;

import java.util.Collection;
import java.util.List;

import com.smu.cs203_g1_1.itinerary_planner.rag.PlaceEmbedding;
import com.smu.cs203_g1_1.itinerary_planner.rag.PlaceEmbeddingRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceDiscoveryServiceTest {

    @Mock
    private LocationRepository locations;

    @Mock
    private PlaceEmbeddingRepository embeddings;

    private PlaceDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new PlaceDiscoveryService(locations, embeddings);
    }

    @Test
    void returnsTheMostSimilarIdsWithinTopK() {
        stubNearby(List.of(location("loc00001"), location("loc00002")));
        when(embeddings.findByLocationIdIn(anyCollection())).thenReturn(List.of(
            new PlaceEmbedding("loc00001", "test", new float[] {1f, 0f}),
            new PlaceEmbedding("loc00002", "test", new float[] {0f, 1f})));

        List<String> ids = List.copyOf(
            service.preferredIds(1.30, 103.85, 3.0, new float[] {1f, 0f}, 1));

        assertEquals(List.of("loc00001"), ids);
    }

    @Test
    void withoutAQueryVectorNothingIsTouched() {
        assertTrue(service.preferredIds(1.30, 103.85, 3.0, null, 5).isEmpty());

        verifyNoInteractions(locations, embeddings);
    }

    @Test
    void returnsEmptyWhenNothingIsEmbeddedNearby() {
        stubNearby(List.of(location("loc00001")));
        when(embeddings.findByLocationIdIn(anyCollection())).thenReturn(List.of());

        assertTrue(service.preferredIds(1.30, 103.85, 3.0, new float[] {1f, 0f}, 5).isEmpty());
    }

    @Test
    void returnsEmptyWhenTheBoxIsEmpty() {
        stubNearby(List.of());

        assertTrue(service.preferredIds(1.30, 103.85, 3.0, new float[] {1f, 0f}, 5).isEmpty());
    }

    private void stubNearby(List<Location> nearby) {
        when(locations.findInBoundingBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(nearby);
    }

    private static Location location(String id) {
        Location location = new Location();
        location.setId(id);
        location.setName("Place " + id);

        return location;
    }
}
