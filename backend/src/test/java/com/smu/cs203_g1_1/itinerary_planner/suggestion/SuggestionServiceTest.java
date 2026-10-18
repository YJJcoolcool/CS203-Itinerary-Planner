package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChoice;
import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChooser;
import com.smu.cs203_g1_1.itinerary_planner.planner.CandidateFinder;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;

import jakarta.persistence.EntityManager;

// Real services against in-memory H2. The weather seam is the only fake, so the
// candidate pipeline, role checks, dirty checking and swap are all exercised.
@DataJpaTest
@Import({SuggestionService.class, TripService.class, CandidateFinder.class,
        SuggestionServiceTest.TestWeatherConfig.class,
        SuggestionServiceTest.TestPlanChooserConfig.class})
class SuggestionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    /** 2026-11-02 is a Monday. */
    private static final ZonedDateTime AT =
            ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZONE);

    @TestConfiguration
    static class TestWeatherConfig {

        static final AtomicBoolean RAIN = new AtomicBoolean(true);

        @Bean
        WeatherSource weatherSource() {
            return (latitude, longitude, from, to) -> RAIN.get();
        }
    }

    @TestConfiguration
    static class TestPlanChooserConfig {

        static final AtomicReference<Optional<PlanChoice>> CHOICE =
                new AtomicReference<>(Optional.empty());
        static final AtomicReference<RuntimeException> FAILURE = new AtomicReference<>();

        @Bean
        PlanChooser planChooser() {
            return request -> {
                RuntimeException failure = FAILURE.get();
                if (failure != null) {
                    throw failure;
                }
                return CHOICE.get();
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
    private EntityManager entityManager;

    private User owner;
    private User viewer;
    private User stranger;
    private Trip trip;
    private Activity activity;
    private Location dropped;
    private Location culture;
    private Location food;

    @BeforeEach
    void setUp() {
        TestWeatherConfig.RAIN.set(true);
        TestPlanChooserConfig.CHOICE.set(Optional.empty());
        TestPlanChooserConfig.FAILURE.set(null);

        owner = saveUser("clerk-owner", "Owner", "owner@test.com");
        viewer = saveUser("clerk-viewer", "Viewer", "viewer@test.com");
        stranger = saveUser("clerk-stranger", "Stranger", "stranger@test.com");

        trip = new Trip("Singapore Weekend",
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), owner);
        trip.setId("Trip0001");
        trip.addMember(owner);
        trip.addMember(viewer, TripRole.VIEWER);
        trip = tripRepository.saveAndFlush(trip);

        dropped = saveLocation("Drop0001", "Gardens by the Bay",
                "culture", "OUTDOOR", 1.300, 103.850, "24/7");
        culture = saveLocation("Cult0001", "National Museum",
                "culture", "INDOOR", 1.303, 103.850, "24/7");
        food = saveLocation("Food0001", "Indoor Food Hall",
                "food", "INDOOR", 1.306, 103.850, "24/7");

        saveScore(culture.getId(), "0.900");
        saveScore(food.getId(), "0.500");

        activity = new Activity("Gardens by the Bay",
                new ArrayList<>(List.of(dropped)),
                AT.plusHours(1), AT.plusHours(2), "PLANNED");
        activity.setId("Act00001");
        activity.setTrip(trip);
        activity = activityRepository.saveAndFlush(activity);
    }

    @Test
    void replanCreatesOnePendingSuggestionWithIndoorProposal() {
        List<SuggestionResponse> created = replan();

        assertEquals(1, created.size());
        SuggestionResponse response = created.get(0);

        assertEquals(SuggestionStatus.PENDING, response.status());
        assertEquals(trip.getId(), response.tripId());
        assertEquals(activity.getId(), response.activityId());
        assertEquals("Gardens by the Bay", response.activityName());
        assertEquals(dropped.getId(), response.currentLocation().id());
        assertEquals(culture.getId(), response.proposedLocation().id());
        assertEquals("INDOOR", locationRepository.findById(culture.getId())
                .orElseThrow().getIndoorClass());
        assertEquals("Rain is expected during Gardens by the Bay.", response.reason());
        assertTrue(response.explanation().contains("indoor option"));
        assertNotNull(response.createdAt());
        assertNull(response.respondedAt());

        Suggestion stored = suggestionRepository.findById(response.id()).orElseThrow();
        assertNotNull(stored.getCandidatesJson());
        assertTrue(stored.getCandidatesJson().contains(culture.getId()));
        assertEquals(culture.getId(), stored.getTopScoreChoiceId());
        assertNull(stored.getAiChoiceId());
    }

    @Test
    void chooserCanPickADifferentCandidate() {
        TestPlanChooserConfig.CHOICE.set(Optional.of(new PlanChoice(
                List.of(new PlanChoice.RankedChoice(food.getId(), 1, 0.9,
                        "Indoor food hall, closer and cheaper.")),
                false)));

        SuggestionResponse response = replan().get(0);

        assertEquals(food.getId(), response.proposedLocation().id());
        assertEquals("Indoor food hall, closer and cheaper.", response.explanation());

        Suggestion stored = suggestionRepository.findById(response.id()).orElseThrow();
        assertEquals(food.getId(), stored.getAiChoiceId());
        assertEquals(culture.getId(), stored.getTopScoreChoiceId());
    }

    @Test
    void chooserWithUnknownIdFallsBackToTopScore() {
        TestPlanChooserConfig.CHOICE.set(Optional.of(new PlanChoice(
                List.of(new PlanChoice.RankedChoice("Nope0001", 1, 0.9, "Invented place.")),
                false)));

        SuggestionResponse response = replan().get(0);

        assertEquals(culture.getId(), response.proposedLocation().id());
        assertTrue(response.explanation().contains("indoor option"));

        Suggestion stored = suggestionRepository.findById(response.id()).orElseThrow();
        assertNull(stored.getAiChoiceId());
        assertEquals(culture.getId(), stored.getTopScoreChoiceId());
    }

    @Test
    void chooserFailureFallsBackToTopScore() {
        TestPlanChooserConfig.FAILURE.set(new IllegalStateException("boom"));

        SuggestionResponse response = replan().get(0);

        assertEquals(culture.getId(), response.proposedLocation().id());
        assertTrue(response.explanation().contains("indoor option"));

        Suggestion stored = suggestionRepository.findById(response.id()).orElseThrow();
        assertNull(stored.getAiChoiceId());
        assertEquals(culture.getId(), stored.getTopScoreChoiceId());
    }

    @Test
    void noRainCreatesNoSuggestion() {
        TestWeatherConfig.RAIN.set(false);

        assertTrue(replan().isEmpty());
        assertEquals(0, suggestionRepository.count());
    }

    @Test
    void secondReplanIsSuppressedByPendingCooldown() {
        assertEquals(1, replan().size());
        long count = suggestionRepository.count();

        assertTrue(replan().isEmpty());
        assertEquals(count, suggestionRepository.count());
    }

    @Test
    void acceptSwapsTheActivityLocationAndMarksAccepted() {
        SuggestionResponse created = replan().get(0);

        SuggestionResponse accepted = suggestionService.accept(created.id(), owner.getId());

        assertEquals(SuggestionStatus.ACCEPTED, accepted.status());
        assertNotNull(accepted.respondedAt());

        entityManager.flush();
        entityManager.clear();

        Activity reloaded = activityRepository.findById(activity.getId()).orElseThrow();
        assertEquals(1, reloaded.getLocations().size());
        assertEquals(culture.getId(), reloaded.getLocations().get(0).getId());
        assertEquals(SuggestionStatus.ACCEPTED,
                suggestionRepository.findById(created.id()).orElseThrow().getStatus());
    }

    @Test
    void viewerCannotAcceptAndAcceptingTwiceIsAConflict() {
        SuggestionResponse created = replan().get(0);

        assertStatus(HttpStatus.FORBIDDEN,
                () -> suggestionService.accept(created.id(), viewer.getId()));

        suggestionService.accept(created.id(), owner.getId());
        assertStatus(HttpStatus.CONFLICT,
                () -> suggestionService.accept(created.id(), owner.getId()));
    }

    @Test
    void rejectMarksRejectedAndRejectingTwiceIsAConflict() {
        SuggestionResponse created = replan().get(0);

        SuggestionResponse rejected = suggestionService.reject(created.id(), owner.getId());
        assertEquals(SuggestionStatus.REJECTED, rejected.status());
        assertNotNull(rejected.respondedAt());

        assertStatus(HttpStatus.CONFLICT,
                () -> suggestionService.reject(created.id(), owner.getId()));
    }

    @Test
    void listChecksMembershipAndUnknownTrip() {
        replan();

        List<SuggestionResponse> visible = suggestionService.list(trip.getId(), viewer.getId());
        assertEquals(1, visible.size());
        assertEquals(SuggestionStatus.PENDING, visible.get(0).status());

        assertStatus(HttpStatus.FORBIDDEN,
                () -> suggestionService.list(trip.getId(), stranger.getId()));
        assertStatus(HttpStatus.NOT_FOUND,
                () -> suggestionService.list("Nope0001", owner.getId()));
    }

    // ----- helpers -----

    private List<SuggestionResponse> replan() {
        return suggestionService.replan(trip.getId(), owner.getId(), AT);
    }

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

    private static void assertStatus(HttpStatus expected, Executable call) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, call);
        assertEquals(expected.value(), exception.getStatusCode().value());
    }
}
