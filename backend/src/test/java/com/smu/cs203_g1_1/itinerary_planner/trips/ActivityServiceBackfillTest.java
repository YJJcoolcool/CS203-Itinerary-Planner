package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.smu.cs203_g1_1.itinerary_planner.discovery.BackfillPolicy;
import com.smu.cs203_g1_1.itinerary_planner.discovery.NearbyBackfillService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ActivityServiceBackfillTest {

    private static final String TRIP_ID = "Trip0001";
    private static final String USER_ID = "User0001";
    private static final ZonedDateTime START =
        ZonedDateTime.parse("2026-11-02T09:00:00+09:00");

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private TripService tripService;

    @Mock
    private LocationService locationService;

    @Mock
    private NearbyBackfillService backfillService;

    @Mock
    private BackfillPolicy backfillPolicy;

    @Mock
    private ObjectProvider<NearbyBackfillService> backfillServiceProvider;

    @Mock
    private ObjectProvider<BackfillPolicy> backfillPolicyProvider;

    private Trip trip;
    private Location location;
    private Activity activity;

    @BeforeEach
    void setUp() {
        User owner = new User("clerk-owner", "Owner");
        owner.setId(USER_ID);
        trip = new Trip("Japan", LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 5), owner);
        trip.setId(TRIP_ID);

        location = new Location("Senso-ji", "Temple", 35.7148, 139.7967, false);
        location.setId("Loc00001");

        activity = new Activity("Visit Senso-ji", List.of(location),
            START, START.plusHours(2), "PLANNED");

        when(tripService.requireTripRole(TRIP_ID, USER_ID, TripRole.EDITOR))
            .thenReturn(trip);
        when(locationService.getLocationById("Loc00001")).thenReturn(location);
        when(activityRepository.save(any(Activity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void creatingALocatedActivitySubmitsOnePrewarm() {
        when(backfillServiceProvider.getIfAvailable()).thenReturn(backfillService);
        when(backfillPolicyProvider.getIfAvailable()).thenReturn(backfillPolicy);
        when(backfillPolicy.shouldBackfill(35.7148, 139.7967)).thenReturn(true);
        when(backfillService.backfillAsync(
                anyDouble(), anyDouble(), anyInt(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(1));

        ActivityService service = new ActivityService(
            activityRepository, tripService, locationService,
            backfillServiceProvider, backfillPolicyProvider, 1500, 120);

        Activity saved = service.createActivity(USER_ID, TRIP_ID, activity);

        assertNotNull(saved);
        InOrder order = inOrder(backfillPolicy, backfillService);
        order.verify(backfillPolicy).recordBackfill(35.7148, 139.7967);
        order.verify(backfillService).backfillAsync(
            eq(35.7148), eq(139.7967), eq(1500), eq(120));
    }

    @Test
    void serviceWithoutBackfillProvidersStillCreatesTheActivity() {
        ActivityService service = new ActivityService(
            activityRepository, tripService, locationService);

        Activity saved = service.createActivity(USER_ID, TRIP_ID, activity);

        assertNotNull(saved);
        verify(activityRepository).save(activity);
        verifyNoInteractions(backfillService, backfillPolicy);
    }
}
