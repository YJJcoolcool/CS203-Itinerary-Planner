package com.smu.cs203_g1_1.itinerary_planner.discovery;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.smu.cs203_g1_1.itinerary_planner.geo.PoiService;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NearbyBackfillServiceTest {

    @Mock
    private PoiService poiService;

    @Mock
    private LocationService locationService;

    private NearbyBackfillService service;

    @BeforeEach
    void setUp() {
        service = new NearbyBackfillService(poiService, locationService, 4);
    }

    @Test
    void importsOnlyUpToTheLimit() {
        when(poiService.findPois(anyDouble(), anyDouble(), eq(1500), anyString(), anyInt()))
            .thenReturn(List.of(
                place("node", 1L),
                place("node", 2L),
                place("node", 3L),
                place("node", 4L),
                place("node", 5L)));

        int imported = service.backfill(3.1390, 101.6869, 1500, 3);

        assertEquals(3, imported);
        verify(locationService).findOrCreateFromOsm("node", 1L);
        verify(locationService).findOrCreateFromOsm("node", 2L);
        verify(locationService).findOrCreateFromOsm("node", 3L);
    }

    @Test
    void dedupesRepeatedOsmIdentitiesAcrossCategories() {
        when(poiService.findPois(anyDouble(), anyDouble(), eq(1500), eq("restaurant"), anyInt()))
            .thenReturn(List.of(place("node", 1L)));
        when(poiService.findPois(anyDouble(), anyDouble(), eq(1500), eq("cafe"), anyInt()))
            .thenReturn(List.of(place("node", 1L), place("way", 2L)));

        int imported = service.backfill(3.1390, 101.6869, 1500, 10);

        assertEquals(2, imported);
        verify(locationService, times(1)).findOrCreateFromOsm("node", 1L);
        verify(locationService, times(1)).findOrCreateFromOsm("way", 2L);
    }

    @Test
    void upstreamFailureReturnsZeroWithoutThrowing() {
        when(poiService.findPois(anyDouble(), anyDouble(), anyInt(), anyString(), anyInt()))
            .thenThrow(new IllegalStateException("Overpass down"));

        int imported = service.backfill(3.1390, 101.6869, 1500, 10);

        assertEquals(0, imported);
        verifyNoInteractions(locationService);
    }

    @Test
    void asyncBackfillCompletesWithTheImportedCount() throws Exception {
        Executor direct = Runnable::run;
        NearbyBackfillService asyncService = new NearbyBackfillService(
            poiService, locationService, 4, direct);
        when(poiService.findPois(anyDouble(), anyDouble(), eq(1500), anyString(), anyInt()))
            .thenReturn(List.of(place("node", 1L)));

        CompletableFuture<Integer> future =
            asyncService.backfillAsync(3.1390, 101.6869, 1500, 10);

        assertEquals(1, future.get(1, TimeUnit.SECONDS));
        verify(locationService).findOrCreateFromOsm("node", 1L);
    }

    @Test
    void rejectedSubmissionCompletesWithZeroInsteadOfThrowing() throws Exception {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("full");
        };
        NearbyBackfillService asyncService = new NearbyBackfillService(
            poiService, locationService, 4, rejecting);

        CompletableFuture<Integer> future =
            asyncService.backfillAsync(3.1390, 101.6869, 1500, 10);

        assertTrue(future.isDone());
        assertEquals(0, future.get(1, TimeUnit.SECONDS));
        verifyNoInteractions(poiService, locationService);
    }

    @Test
    void ignoresPlacesWithoutAnOsmIdentity() {
        PlaceDto noType = new PlaceDto(null, 1L, "No type", "No type", "amenity",
            "cafe", 3.1, 101.6, null, null, null, null);
        PlaceDto noId = new PlaceDto("node", null, "No id", "No id", "amenity",
            "cafe", 3.1, 101.6, null, null, null, null);

        when(poiService.findPois(anyDouble(), anyDouble(), anyInt(), anyString(), anyInt()))
            .thenReturn(List.of(noType, noId));

        int imported = service.backfill(3.1390, 101.6869, 1500, 10);

        assertEquals(0, imported);
        verifyNoInteractions(locationService);
    }

    private static PlaceDto place(String type, Long id) {
        return new PlaceDto(type, id, "Place " + id, "Place " + id, "amenity",
            "cafe", 3.1, 101.6, null, null, null, null);
    }
}
