package com.smu.cs203_g1_1.itinerary_planner.polling;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChooser;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.planner.CandidateFinder;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.Suggestion;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionRepository;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionResponse;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionService;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionStatus;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.TriggerSource;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.testutil.MutableClock;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;
import com.smu.cs203_g1_1.itinerary_planner.weather.DefaultWeatherSourceResolver;
import com.smu.cs203_g1_1.itinerary_planner.weather.ProgrammableWeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

/**
 * End-to-end simulation of the polling + filtering path against H2 with no
 * network: a programmable weather gate, a counting plan chooser that always
 * falls back to the deterministic pick, and the real suggestion service.
 */
@DataJpaTest
@Import({SuggestionService.class, TripService.class, CandidateFinder.class,
        DefaultWeatherSourceResolver.class, RegionRegistry.class,
        PollingSimulationTest.TestClockConfig.class,
        PollingSimulationTest.TestWeatherConfig.class,
        PollingSimulationTest.TestPlanChooserConfig.class})
class PollingSimulationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    /** 2026-11-02 is a Monday. */
    private static final ZonedDateTime AT =
            ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZONE);

    @TestConfiguration
    static class TestClockConfig {

        static final MutableClock CLOCK = new MutableClock(AT.toInstant(), ZONE);

        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    @TestConfiguration
    static class TestWeatherConfig {

        static final ProgrammableWeatherSource WEATHER = new ProgrammableWeatherSource();

        @Bean
        WeatherSource weatherSource() {
            return WEATHER;
        }
    }

    @TestConfiguration
    static class TestPlanChooserConfig {

        static final AtomicInteger CALLS = new AtomicInteger();

        @Bean
        PlanChooser planChooser() {
            return request -> {
                CALLS.incrementAndGet();
                return Optional.empty();
            };
        }
    }

    @Autowired
    private SuggestionService suggestionService;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlaceScoreRepository placeScoreRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private WeatherSourceResolver weatherSourceResolver;

    @Autowired
    private RegionRegistry regionRegistry;

    private WeatherPoller poller;
    private User owner;
    private Trip trip;
    private Location dropped;
    private Location culture;
    private Location food;

    @BeforeEach
    void setUp() {
        TestWeatherConfig.WEATHER.reset();
        TestPlanChooserConfig.CALLS.set(0);
        TestClockConfig.CLOCK.setInstant(AT.toInstant());

        poller = new WeatherPoller(tripRepository, activityRepository,
                suggestionService, weatherSourceResolver, regionRegistry, clock);

        owner = saveUser("clerk-owner", "Owner", "owner@simulation.test");

        trip = new Trip("Singapore Weekend",
                AT.toLocalDate(), AT.toLocalDate().plusDays(2), owner);
        trip.setId("Trip0001");
        trip = tripRepository.saveAndFlush(trip);

        dropped = saveLocation("Drop0001", "Gardens by the Bay",
                "culture", "OUTDOOR", 1.300, 103.850, "24/7");
        culture = saveLocation("Cult0001", "National Museum",
                "culture", "INDOOR", 1.303, 103.850, "24/7");
        food = saveLocation("Food0001", "Indoor Food Hall",
                "food", "INDOOR", 1.306, 103.850, "24/7");

        saveScore(culture.getId(), "0.900");
        saveScore(food.getId(), "0.500");
    }

    @Test
    void rainyUpcomingTickCreatesPollImminentSuggestion() {
        ZonedDateTime start = AT.plusMinutes(100);
        ZonedDateTime end = AT.plusMinutes(160);
        saveActivity("Act00001", start, end, dropped);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollUpcomingActivities();

        List<Suggestion> suggestions = suggestionRepository.findAll();
        assertEquals(1, suggestions.size());

        Suggestion suggestion = suggestions.get(0);
        assertEquals(SuggestionStatus.PENDING, suggestion.getStatus());
        assertEquals(TriggerSource.POLL_IMMINENT, suggestion.getTriggerSource());
        assertEquals(dropped.getId(), suggestion.getCurrentLocation().getId());
        assertEquals("INDOOR", suggestion.getProposedLocation().getIndoorClass());
        assertEquals(1, TestPlanChooserConfig.CALLS.get());

        // Lock the decision: one gate call plus the service's own re-check.
        List<ProgrammableWeatherSource.Call> calls = TestWeatherConfig.WEATHER.calls();
        assertEquals(2, calls.size());

        for (ProgrammableWeatherSource.Call call : calls) {
            assertEquals(start, call.from());
            assertEquals(end, call.to());
            assertEquals(dropped.getLatitude(), call.latitude(), 1e-9);
            assertEquals(dropped.getLongitude(), call.longitude(), 1e-9);
        }
    }

    @Test
    void dryUpcomingTickSkipsDiscoveryAndLlm() {
        ZonedDateTime start = AT.plusMinutes(100);
        ZonedDateTime end = AT.plusMinutes(160);
        saveActivity("Act00001", start, end, dropped);
        TestWeatherConfig.WEATHER.setRainy(false);

        poller.pollUpcomingActivities();

        assertEquals(0, suggestionRepository.count());
        assertEquals(0, TestPlanChooserConfig.CALLS.get());

        List<ProgrammableWeatherSource.Call> calls = TestWeatherConfig.WEATHER.calls();
        assertEquals(1, calls.size());
        assertEquals(start, calls.get(0).from());
        assertEquals(end, calls.get(0).to());
        assertEquals(dropped.getLatitude(), calls.get(0).latitude(), 1e-9);
        assertEquals(dropped.getLongitude(), calls.get(0).longitude(), 1e-9);
    }

    @Test
    void indoorOnlyStopCreatesNoSuggestion() {
        saveActivity("Act00001", AT.plusMinutes(100), AT.plusMinutes(160), culture);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollUpcomingActivities();

        assertEquals(0, suggestionRepository.count());
        assertEquals(0, TestPlanChooserConfig.CALLS.get());
        // No outdoor stop means the tick never even asks the weather feed.
        assertEquals(0, TestWeatherConfig.WEATHER.calls().size());
    }

    @Test
    void activitiesOutsideTheWindowAreNotEvenWeatherChecked() {
        saveActivity("Act00001", AT.plusMinutes(80), AT.plusMinutes(100), dropped);
        saveActivity("Act00002", AT.plusMinutes(160), AT.plusMinutes(180), dropped);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollUpcomingActivities();

        assertEquals(0, suggestionRepository.count());
        assertEquals(0, TestPlanChooserConfig.CALLS.get());
        assertEquals(0, TestWeatherConfig.WEATHER.calls().size());
    }

    @Test
    void rainyTomorrowTripCreatesPollTomorrowSuggestionFromTheOutdoorCentroid() {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        trip.setStartDate(tomorrow);
        trip.setEndDate(tomorrow.plusDays(1));
        tripRepository.saveAndFlush(trip);

        ZonedDateTime start = tomorrow.atTime(10, 0).atZone(ZONE);
        ZonedDateTime end = start.plusHours(1);
        saveActivity("Act00001", start, end, dropped, culture);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollTomorrowTrips();

        List<Suggestion> suggestions = suggestionRepository.findAll();
        assertEquals(1, suggestions.size());

        Suggestion suggestion = suggestions.get(0);
        assertEquals(SuggestionStatus.PENDING, suggestion.getStatus());
        assertEquals(TriggerSource.POLL_TOMORROW, suggestion.getTriggerSource());
        assertEquals(1, TestPlanChooserConfig.CALLS.get());

        ZonedDateTime dayFrom = tomorrow.atStartOfDay(ZONE);
        ZonedDateTime dayTo = tomorrow.plusDays(1).atStartOfDay(ZONE);

        ProgrammableWeatherSource.Call centroidCall = TestWeatherConfig.WEATHER.calls().stream()
                .filter(candidate -> dayFrom.equals(candidate.from())
                        && dayTo.equals(candidate.to()))
                .findFirst()
                .orElseThrow();

        // culture is indoor, so the centroid is the outdoor stop alone.
        assertEquals(dropped.getLatitude(), centroidCall.latitude(), 1e-9);
        assertEquals(dropped.getLongitude(), centroidCall.longitude(), 1e-9);
    }

    @Test
    void dryTomorrowTripCreatesNoSuggestion() {
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        trip.setStartDate(tomorrow);
        trip.setEndDate(tomorrow.plusDays(1));
        tripRepository.saveAndFlush(trip);

        ZonedDateTime start = tomorrow.atTime(10, 0).atZone(ZONE);
        saveActivity("Act00001", start, start.plusHours(1), dropped);
        TestWeatherConfig.WEATHER.setRainy(false);

        poller.pollTomorrowTrips();

        assertEquals(0, suggestionRepository.count());
        assertEquals(0, TestPlanChooserConfig.CALLS.get());
        assertEquals(1, TestWeatherConfig.WEATHER.calls().size());
    }

    @Test
    void secondRainyTickIsSuppressedByThePendingSuggestion() {
        saveActivity("Act00001", AT.plusMinutes(100), AT.plusMinutes(160), dropped);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollUpcomingActivities();
        poller.pollUpcomingActivities();

        List<Suggestion> suggestions = suggestionRepository.findAll();
        assertEquals(1, suggestions.size());
        assertEquals(SuggestionStatus.PENDING, suggestions.get(0).getStatus());
        assertEquals(TriggerSource.POLL_IMMINENT, suggestions.get(0).getTriggerSource());
        assertEquals(1, TestPlanChooserConfig.CALLS.get());
    }

    @Test
    void rejectedPollerSuggestionIsNotReproposed() {
        saveActivity("Act00001", AT.plusMinutes(100), AT.plusMinutes(160), dropped);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollUpcomingActivities();
        Suggestion created = suggestionRepository.findAll().get(0);
        suggestionService.reject(created.getId(), owner.getId());

        poller.pollUpcomingActivities();

        List<Suggestion> suggestions = suggestionRepository.findAll();
        assertEquals(1, suggestions.size());
        assertEquals(SuggestionStatus.REJECTED, suggestions.get(0).getStatus());
    }

    @Test
    void manualReplanStillWorksAfterReject() {
        saveActivity("Act00001", AT.plusMinutes(100), AT.plusMinutes(160), dropped);
        TestWeatherConfig.WEATHER.setRainy(true);

        poller.pollUpcomingActivities();
        Suggestion created = suggestionRepository.findAll().get(0);
        suggestionService.reject(created.getId(), owner.getId());

        List<SuggestionResponse> manual =
                suggestionService.replan(trip.getId(), owner.getId(), AT);

        assertEquals(1, manual.size());
        assertEquals(2, suggestionRepository.count());
    }

    // ----- helpers -----

    private User saveUser(String clerkId, String displayName, String email) {
        User user = new User(clerkId, displayName);
        user.setId(ID.generateID());
        user.setEmail(email);
        return userRepository.saveAndFlush(user);
    }

    private Location saveLocation(String id, String name, String categoryGroup,
                                  String indoorClass, double latitude, double longitude,
                                  String openingHours) {
        Location location = new Location(name, "test", latitude, longitude,
                "INDOOR".equals(indoorClass));
        location.setId(id);
        location.setSeeded(true);
        location.setCategoryGroup(categoryGroup);
        location.setIndoorClass(indoorClass);
        location.setIndoorSource("MANUAL");
        location.setOpeningHours(openingHours);
        return locationRepository.saveAndFlush(location);
    }

    private void saveScore(String locationId, String quality) {
        placeScoreRepository.saveAndFlush(new PlaceScore(
                locationId,
                "MANUAL",
                new BigDecimal(quality),
                new BigDecimal("0.500"),
                Instant.parse("2026-10-18T02:00:00Z")));
    }

    private Activity saveActivity(String id, ZonedDateTime start, ZonedDateTime end,
                                  Location... locations) {
        Activity activity = new Activity("Activity " + id,
                new ArrayList<>(List.of(locations)), start, end, "PLANNED");
        activity.setId(id);
        activity.setTrip(trip);
        return activityRepository.saveAndFlush(activity);
    }
}
