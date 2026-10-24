package com.smu.cs203_g1_1.itinerary_planner.polling;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionResponse;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionService;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.TriggerSource;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;

@ExtendWith(MockitoExtension.class)
class WeatherPollerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZONE);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SuggestionService suggestionService;

    @Mock
    private WeatherSource weatherSource;

    private WeatherPoller poller;

    private User owner;
    private Trip trip;
    private Location location;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZONE);
        poller = new WeatherPoller(tripRepository, activityRepository,
                suggestionService, weatherSource, clock);

        owner = new User("clerk-owner", "Owner");
        owner.setId("User0001");

        trip = new Trip("Singapore Weekend",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), owner);
        trip.setId("Trip0001");

        location = new Location("Gardens by the Bay", "culture",
                1.300, 103.850, false);
        location.setId("Loc00001");
        location.setIndoorClass("OUTDOOR");
    }

    @Test
    void upcomingPassUsesNinetyToHundredFiftyMinuteWindow() {
        Activity activity = activity("Act00001", NOW.plusMinutes(100), NOW.plusMinutes(160));
        when(activityRepository.findByStartDateTimeBetween(
                NOW.plusMinutes(90), NOW.plusMinutes(150)))
                .thenReturn(List.of(activity));
        when(weatherSource.isRainy(eq(1.300), eq(103.850),
                eq(activity.getStartDateTime()), eq(activity.getEndDateTime())))
                .thenReturn(true);
        when(tripRepository.findById("Trip0001")).thenReturn(Optional.of(trip));
        when(suggestionService.replanForSystem(
                anyString(), anyString(), any(), any()))
                .thenReturn(List.of());

        poller.pollUpcomingActivities();

        // The repository decides which rows are in the band; the poller must ask
        // for exactly +90m..+150m.
        verify(activityRepository).findByStartDateTimeBetween(
                NOW.plusMinutes(90), NOW.plusMinutes(150));
        verify(suggestionService).replanForSystem(
                "Trip0001", "User0001", NOW, TriggerSource.POLL_IMMINENT);
    }

    @Test
    void upcomingPassUsesStartPlusTwoHoursWhenEndIsMissing() {
        Activity activity = activity("Act00001", NOW.plusMinutes(100), null);
        when(activityRepository.findByStartDateTimeBetween(
                NOW.plusMinutes(90), NOW.plusMinutes(150)))
                .thenReturn(List.of(activity));
        when(weatherSource.isRainy(eq(1.300), eq(103.850),
                eq(activity.getStartDateTime()), eq(activity.getStartDateTime().plusHours(2))))
                .thenReturn(true);
        when(tripRepository.findById("Trip0001")).thenReturn(Optional.of(trip));
        when(suggestionService.replanForSystem(
                anyString(), anyString(), any(), any()))
                .thenReturn(List.of());

        poller.pollUpcomingActivities();

        verify(weatherSource).isRainy(1.300, 103.850,
                activity.getStartDateTime(), activity.getStartDateTime().plusHours(2));
    }

    @Test
    void tomorrowPassTargetsTripsStartingTomorrow() {
        LocalDate tomorrow = LocalDate.now(Clock.fixed(NOW.toInstant(), ZONE)).plusDays(1);
        trip.setStartDate(tomorrow);

        Location indoor = indoor("Ind00001", 1.400, 103.900);
        Activity tomorrowActivity = activity("Act00001", NOW.plusMinutes(100),
                NOW.plusMinutes(160), trip, indoor, location);

        when(tripRepository.findByStartDate(tomorrow)).thenReturn(List.of(trip));
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc("Trip0001"))
                .thenReturn(List.of(tomorrowActivity));
        when(weatherSource.isRainy(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(false);

        poller.pollTomorrowTrips();

        verify(tripRepository).findByStartDate(tomorrow);
        // The centroid averages outdoor stops only, never the indoor one.
        verify(weatherSource).isRainy(
                eq(location.getLatitude()), eq(location.getLongitude()),
                eq(tomorrow.atStartOfDay(ZONE)),
                eq(tomorrow.plusDays(1).atStartOfDay(ZONE)));
        verifyNoInteractions(suggestionService);
    }

    @Test
    void gatesOnTheOutdoorLocationNotTheFirstIndoorOne() {
        Location indoor = indoor("Ind00001", 1.400, 103.900);
        Activity activity = activity("Act00001", NOW.plusMinutes(100),
                NOW.plusMinutes(160), trip, indoor, location);

        when(activityRepository.findByStartDateTimeBetween(any(), any()))
                .thenReturn(List.of(activity));
        when(weatherSource.isRainy(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(true);
        when(tripRepository.findById("Trip0001")).thenReturn(Optional.of(trip));
        when(suggestionService.replanForSystem(anyString(), anyString(), any(), any()))
                .thenReturn(List.<SuggestionResponse>of());

        poller.pollUpcomingActivities();

        verify(weatherSource).isRainy(
                eq(location.getLatitude()), eq(location.getLongitude()),
                eq(activity.getStartDateTime()), eq(activity.getEndDateTime()));
        verify(suggestionService).replanForSystem(
                "Trip0001", "User0001", NOW, TriggerSource.POLL_IMMINENT);
    }

    @Test
    void indoorOnlyActivityIsNotWeatherChecked() {
        Location indoor = indoor("Ind00001", 1.400, 103.900);
        Activity activity = activity("Act00001", NOW.plusMinutes(100),
                NOW.plusMinutes(160), trip, indoor);

        when(activityRepository.findByStartDateTimeBetween(any(), any()))
                .thenReturn(List.of(activity));

        poller.pollUpcomingActivities();

        verifyNoInteractions(weatherSource);
        verifyNoInteractions(suggestionService);
    }

    @Test
    void dryWeatherNeverCallsTheSuggestionService() {
        Activity upcoming = activity("Act00001", NOW.plusMinutes(100), NOW.plusMinutes(160));
        Activity tomorrow = activity("Act00002", NOW.plusMinutes(120), NOW.plusMinutes(180));

        when(activityRepository.findByStartDateTimeBetween(any(), any()))
                .thenReturn(List.of(upcoming));
        when(tripRepository.findByStartDate(any()))
                .thenReturn(List.of(trip));
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc("Trip0001"))
                .thenReturn(List.of(tomorrow));
        when(weatherSource.isRainy(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(false);

        poller.pollUpcomingActivities();
        poller.pollTomorrowTrips();

        verifyNoInteractions(suggestionService);
    }

    @Test
    void rainyTickReplansOncePerDistinctTrip() {
        Activity first = activity("Act00001", NOW.plusMinutes(100), NOW.plusMinutes(160));
        Activity second = activity("Act00002", NOW.plusMinutes(100), NOW.plusMinutes(160));

        when(activityRepository.findByStartDateTimeBetween(any(), any()))
                .thenReturn(List.of(first, second));
        when(weatherSource.isRainy(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(true);
        when(tripRepository.findById("Trip0001")).thenReturn(Optional.of(trip));
        when(suggestionService.replanForSystem(
                anyString(), anyString(), any(), any()))
                .thenReturn(List.<SuggestionResponse>of());

        poller.pollUpcomingActivities();

        verify(suggestionService, times(1)).replanForSystem(
                "Trip0001", "User0001", NOW, TriggerSource.POLL_IMMINENT);
    }

    @Test
    void oneFailingTripDoesNotStopTheOthers() {
        LocalDate tomorrow = LocalDate.now(Clock.fixed(NOW.toInstant(), ZONE)).plusDays(1);
        Trip bad = trip("Trip0001", tomorrow);
        Trip good = trip("Trip0002", tomorrow);

        when(tripRepository.findByStartDate(tomorrow)).thenReturn(List.of(bad, good));
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc("Trip0001"))
                .thenThrow(new IllegalStateException("boom"));
        when(activityRepository.findByTripIdOrderByStartDateTimeAsc("Trip0002"))
                .thenReturn(List.of(activity("Act00002", NOW.plusMinutes(100),
                        NOW.plusMinutes(160), good)));
        when(weatherSource.isRainy(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(true);
        when(suggestionService.replanForSystem(
                anyString(), anyString(), any(), any()))
                .thenReturn(List.<SuggestionResponse>of());

        assertDoesNotThrow(poller::pollTomorrowTrips);

        verify(suggestionService, never()).replanForSystem(
                eq("Trip0001"), anyString(), any(), any());
        verify(suggestionService).replanForSystem(
                eq("Trip0002"), eq("User0001"), any(), eq(TriggerSource.POLL_TOMORROW));
    }

    // ----- helpers -----

    private Activity activity(String id, ZonedDateTime start, ZonedDateTime end) {
        return activity(id, start, end, trip, location);
    }

    private Activity activity(String id, ZonedDateTime start, ZonedDateTime end, Trip ownerTrip) {
        return activity(id, start, end, ownerTrip, location);
    }

    private Activity activity(String id, ZonedDateTime start, ZonedDateTime end,
                              Trip ownerTrip, Location... locations) {
        Activity activity = new Activity("Gardens by the Bay",
                new ArrayList<>(List.of(locations)), start, end, "PLANNED");
        activity.setId(id);
        activity.setTrip(ownerTrip);
        return activity;
    }

    private static Location indoor(String id, double latitude, double longitude) {
        Location indoor = new Location("Indoor stop", "culture", latitude, longitude, true);
        indoor.setId(id);
        indoor.setIndoorClass("INDOOR");
        return indoor;
    }

    private Trip trip(String id, LocalDate startDate) {
        Trip result = new Trip("Trip " + id, startDate, startDate.plusDays(1), owner);
        result.setId(id);
        return result;
    }
}
