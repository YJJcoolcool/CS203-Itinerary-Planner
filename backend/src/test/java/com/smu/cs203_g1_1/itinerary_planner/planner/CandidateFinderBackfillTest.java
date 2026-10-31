package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.smu.cs203_g1_1.itinerary_planner.discovery.BackfillPolicy;
import com.smu.cs203_g1_1.itinerary_planner.discovery.NearbyBackfillService;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CandidateFinderBackfillTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
    private static final ZonedDateTime ARRIVAL =
        ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZONE);
    private static final Instant NOW = Instant.parse("2026-11-02T07:00:00Z");
    private static final String DROPPED_ID = "DropMy01";

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlaceScoreRepository placeScoreRepository;

    @BeforeEach
    void setUp() {
        saveLocation(DROPPED_ID, 3.1390, 101.6869, "culture", "INDOOR",
            "24/7", "my");
    }

    @Test
    void submitsBackfillOnceWithoutJoiningTheFuture() {
        LocationRepository repository = mock(LocationRepository.class);
        PlaceScoreRepository scores = mock(PlaceScoreRepository.class);
        NearbyBackfillService backfillService = mock(NearbyBackfillService.class);
        BackfillPolicy policy = spy(new BackfillPolicy(
            Clock.fixed(NOW, ZoneOffset.UTC), true));
        CandidateFinder finder = finder(repository, scores, policy, backfillService);

        when(repository.findById(DROPPED_ID))
            .thenReturn(Optional.of(location(DROPPED_ID, 3.1390, 101.6869, "my")));
        when(repository.findTrustedInBoundingBoxAndCountry(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("my")))
            .thenReturn(List.of());

        CompletableFuture<Integer> neverCompletes = new CompletableFuture<>();
        when(backfillService.backfillAsync(
                anyDouble(), anyDouble(), anyInt(), anyInt()))
            .thenReturn(neverCompletes);

        List<Candidate> first = assertTimeoutPreemptively(Duration.ofSeconds(2),
            () -> finder.find(request()));
        List<Candidate> second = finder.find(request());

        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());

        InOrder order = inOrder(policy, backfillService);
        order.verify(policy).recordBackfill(eq(3.1390), eq(101.6869));
        order.verify(backfillService).backfillAsync(
            eq(3.1390), eq(101.6869), eq(1500), eq(120));

        verify(backfillService, times(1))
            .backfillAsync(anyDouble(), anyDouble(), anyInt(), anyInt());
        verify(policy, times(2)).shouldBackfill(anyDouble(), anyDouble());
    }

    @Test
    void countryFilterExcludesTheNeighbouringCountryRow() {
        NearbyBackfillService backfillService = mock(NearbyBackfillService.class);
        BackfillPolicy policy = new BackfillPolicy(
            Clock.fixed(NOW, ZoneOffset.UTC), false);
        CandidateFinder finder = finder(
            locationRepository, placeScoreRepository, policy, backfillService);

        saveLocation("MyPlace1", 3.1400, 101.6880, "culture", "INDOOR",
            "24/7", "my");
        saveLocation("SgNeigh1", 3.1390, 101.6869, "culture", "INDOOR",
            "24/7", "sg");

        List<Candidate> result = finder.find(request());

        assertTrue(result.stream().allMatch(candidate -> "MyPlace1"
            .equals(candidate.location().getId())));
        verifyNoInteractions(backfillService);
    }

    @SuppressWarnings("unchecked")
    private CandidateFinder finder(LocationRepository repository,
                                   PlaceScoreRepository scores,
                                   BackfillPolicy policy,
                                   NearbyBackfillService backfillService) {
        ObjectProvider<NearbyBackfillService> serviceProvider = mock(ObjectProvider.class);
        ObjectProvider<BackfillPolicy> policyProvider = mock(ObjectProvider.class);
        when(serviceProvider.getIfAvailable()).thenReturn(backfillService);
        when(policyProvider.getIfAvailable()).thenReturn(policy);

        return new CandidateFinder(
            repository,
            scores,
            new ScoreCalculator(),
            serviceProvider,
            policyProvider,
            40,
            1500,
            120);
    }

    private CandidateRequest request() {
        return new CandidateRequest(
            DROPPED_ID,
            ARRIVAL,
            true,
            Set.of("culture"),
            10,
            Set.of(),
            Set.of());
    }

    private void saveLocation(String id, double latitude, double longitude,
                              String categoryGroup, String indoorClass,
                              String openingHours, String countryCode) {
        Location location = location(id, latitude, longitude, countryCode);
        location.setCategoryGroup(categoryGroup);
        location.setIndoorClass(indoorClass);
        location.setOpeningHours(openingHours);
        locationRepository.saveAndFlush(location);
    }

    private static Location location(String id, double latitude,
                                     double longitude, String countryCode) {
        Location location = new Location(
            "Place " + id,
            "test",
            latitude,
            longitude,
            true);
        location.setId(id);
        location.setSeeded(true);
        location.setCountryCode(countryCode);
        return location;
    }
}
