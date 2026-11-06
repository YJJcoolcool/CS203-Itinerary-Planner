package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChoice;
import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChoiceRequest;
import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChoiceValidator;
import com.smu.cs203_g1_1.itinerary_planner.ai.PlanChooser;
import com.smu.cs203_g1_1.itinerary_planner.discovery.PlaceDiscoveryService;
import com.smu.cs203_g1_1.itinerary_planner.planner.Candidate;
import com.smu.cs203_g1_1.itinerary_planner.planner.CandidateFinder;
import com.smu.cs203_g1_1.itinerary_planner.planner.CandidateRequest;
import com.smu.cs203_g1_1.itinerary_planner.planner.CategoryGoalMapper;
import com.smu.cs203_g1_1.itinerary_planner.planner.OpeningHours;
import com.smu.cs203_g1_1.itinerary_planner.preference.TripPreference;
import com.smu.cs203_g1_1.itinerary_planner.preference.TripPreferenceRepository;
import com.smu.cs203_g1_1.itinerary_planner.preference.UserPreferenceRepository;
import com.smu.cs203_g1_1.itinerary_planner.rag.QueryEmbedder;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.util.ID;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

/**
 * Builds weather-driven alternative suggestions and applies the user's decision.
 *
 * <p>Replanning scans the trip's outdoor stops, asks the {@link WeatherSource}
 * whether rain intersects each stop's window, and asks the {@link PlanChooser}
 * to re-rank the deterministic shortlist. When the chooser is unavailable or
 * its answer fails validation, the top deterministic candidate is stored
 * instead. Accepting swaps the one affected location in the same transaction
 * as the status change.</p>
 */
@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private static final int MAX_SUGGESTIONS_PER_REPLAN = 3;
    private static final int WINDOW_HOURS = 24;
    private static final int SHORTLIST_LIMIT = 5;
    private static final int OPTION_LIMIT = 5;
    private static final int RAIN_WINDOW_FALLBACK_HOURS = 2;
    private static final int SEMANTIC_SHORTLIST = 8;

    private final SuggestionRepository suggestionRepository;
    private final ActivityRepository activityRepository;
    private final LocationRepository locationRepository;
    private final TripService tripService;
    private final CandidateFinder candidateFinder;
    private final WeatherSourceResolver weatherSourceResolver;
    private final PlanChooser planChooser;
    private final ObjectProvider<QueryEmbedder> queryEmbedder;
    private final ObjectProvider<PlaceDiscoveryService> discoveryService;
    private final UserPreferenceRepository userPreferences;
    private final TripPreferenceRepository tripPreferences;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SuggestionService(SuggestionRepository suggestionRepository,
                             ActivityRepository activityRepository,
                             LocationRepository locationRepository,
                             TripService tripService,
                             CandidateFinder candidateFinder,
                             WeatherSourceResolver weatherSourceResolver,
                             PlanChooser planChooser,
                             ObjectProvider<QueryEmbedder> queryEmbedder,
                             ObjectProvider<PlaceDiscoveryService> discoveryService,
                             UserPreferenceRepository userPreferences,
                             TripPreferenceRepository tripPreferences,
                             Clock clock) {
        this.suggestionRepository = suggestionRepository;
        this.activityRepository = activityRepository;
        this.locationRepository = locationRepository;
        this.tripService = tripService;
        this.candidateFinder = candidateFinder;
        this.weatherSourceResolver = weatherSourceResolver;
        this.planChooser = planChooser;
        this.queryEmbedder = queryEmbedder;
        this.discoveryService = discoveryService;
        this.userPreferences = userPreferences;
        this.tripPreferences = tripPreferences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> list(String tripId, String userId) {
        tripService.getTripForMember(tripId, userId);
        return suggestionRepository.findByTripIdOrderByCreatedAtDesc(tripId)
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional
    public SuggestionResponse accept(String suggestionId, String userId) {
        return accept(suggestionId, userId, null);
    }

    @Transactional
    public SuggestionResponse accept(String suggestionId, String userId, String locationId) {
        Suggestion suggestion = loadSuggestion(suggestionId);
        tripService.requireTripRole(suggestion.getTrip().getId(), userId, TripRole.EDITOR);
        requirePending(suggestion);

        Activity activity = suggestion.getActivity();
        Location current = suggestion.getCurrentLocation();
        Location proposed = suggestion.getProposedLocation();

        String chosenId = locationId == null || locationId.isBlank()
                ? proposed.getId()
                : locationId.trim();

        List<SuggestionResponse.Option> options = SuggestionResponse.from(suggestion).options();
        boolean storedOption = options.stream()
                .anyMatch(option -> option.location() != null
                        && chosenId != null
                        && chosenId.equals(option.location().id()));
        if (!storedOption) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown alternative for this suggestion");
        }

        Location chosen;
        if (proposed.getId() != null && proposed.getId().equals(chosenId)) {
            chosen = proposed;
        } else {
            chosen = locationRepository.findById(chosenId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown alternative for this suggestion"));
        }

        List<Location> locations = activity.getLocations();
        int index = indexOf(locations, current);
        if (current == null || index < 0) {
            log.warn("Suggestion id={} is stale: activity id={} no longer contains location id={}",
                    suggestionId, activity.getId(),
                    current == null ? null : current.getId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The activity no longer contains the suggested location");
        }

        locations.set(index, chosen);
        activityRepository.save(activity);

        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        suggestion.setRespondedAt(Instant.now(clock));
        Suggestion saved = suggestionRepository.save(suggestion);

        log.info("Accepted suggestion id={} for activity id={} on trip id={}",
                suggestionId, activity.getId(), suggestion.getTrip().getId());
        return SuggestionResponse.from(saved);
    }

    @Transactional
    public SuggestionResponse reject(String suggestionId, String userId) {
        Suggestion suggestion = loadSuggestion(suggestionId);
        tripService.requireTripRole(suggestion.getTrip().getId(), userId, TripRole.EDITOR);
        requirePending(suggestion);

        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setRespondedAt(Instant.now(clock));
        Suggestion saved = suggestionRepository.save(suggestion);

        log.info("Rejected suggestion id={} for activity id={} on trip id={}",
                suggestionId, suggestion.getActivity().getId(), suggestion.getTrip().getId());
        return SuggestionResponse.from(saved);
    }

    /** Union of the traveller's own tags and the trip's group tags. */
    private Set<String> interests(String userId, TripPreference tripPreference) {
        Set<String> tags = new TreeSet<>();

        userPreferences.findById(userId)
            .ifPresent(preference -> tags.addAll(preference.getTags()));

        if (tripPreference != null) {
            tags.addAll(tripPreference.getTags());
        }

        return tags;
    }

    /**
     * Embeds the trip context once per replan, preferring the trip goal and the
     * group summary over the trip name. Returns null when semantic retrieval is
     * unavailable, in which case discovery stays SQL-only.
     */
    private float[] buildQueryVector(Trip trip, Set<String> interests, TripPreference preference) {
        QueryEmbedder embedder = queryEmbedder.getIfAvailable();

        if (embedder == null) {
            return null;
        }

        String goal = preference == null || preference.getGoals().isEmpty()
            ? trip.getName()
            : String.join(", ", preference.getGoals());
        String summary = preference == null ? null : preference.getSummary();

        return embedder.embedQuery(
                goal,
                summary,
                interests,
                "rain expected within the next 24 hours; prefer indoor alternatives")
            .orElse(null);
    }

    /**
     * Looks for outdoor stops that rain is expected to affect and creates at
     * most {@value #MAX_SUGGESTIONS_PER_REPLAN} pending suggestions. A pending
     * suggestion for a stop suppresses new ones until it is decided.
     */
    @Transactional
    public List<SuggestionResponse> replan(String tripId, String userId, ZonedDateTime at) {
        Trip trip = tripService.requireTripRole(tripId, userId, TripRole.EDITOR);
        return replanInternal(trip, userId, at, TriggerSource.MANUAL);
    }

    /**
     * Poller entry point. Authorization comes from the caller's trust boundary
     * (the scheduler), so only the EDITOR check is skipped; every other rule is
     * shared with the manual path.
     */
    @Transactional
    public List<SuggestionResponse> replanForSystem(String tripId, String userId,
                                                    ZonedDateTime at, TriggerSource trigger) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(trigger, "trigger");
        if (trigger == TriggerSource.MANUAL) {
            throw new IllegalArgumentException("replanForSystem requires a poll trigger");
        }

        Trip trip = tripService.getTrip(tripId);
        return replanInternal(trip, userId, at, trigger);
    }

    private List<SuggestionResponse> replanInternal(Trip trip, String userId,
                                                    ZonedDateTime at, TriggerSource trigger) {
        String tripId = trip.getId();
        ZonedDateTime now = at != null ? at : ZonedDateTime.now(clock);
        long startedAt = System.nanoTime();

        if (weatherSourceResolver.forCountry(trip.getCountryCode()).isEmpty()) {
            log.info("Skipping replan for trip id={}: no weather provider for country {}",
                    tripId, trip.getCountryCode());
            return List.of();
        }

        TripPreference tripPreference = tripPreferences.findById(tripId).orElse(null);
        Set<String> interests = interests(userId, tripPreference);

        // Raw tags feed the embedder and the LLM prompt; scoring needs the
        // canonical lowercase groups derived from both tags and trip goals.
        Set<String> candidateInterests = new TreeSet<>(
                CategoryGoalMapper.toGroups(interests));
        candidateInterests.addAll(CategoryGoalMapper.toGroups(
                tripPreference == null ? List.of() : tripPreference.getGoals()));

        String tripGoal = tripPreference == null || tripPreference.getGoals().isEmpty()
                ? null
                : String.join(", ", tripPreference.getGoals());
        String groupSummary = tripPreference == null ? null : tripPreference.getSummary();

        // One embedding call per replan; every activity reuses the same vector.
        float[] queryVector = buildQueryVector(trip, interests, tripPreference);

        List<Activity> activities = activityRepository.findByTripIdOrderByStartDateTimeAsc(tripId);
        List<SuggestionResponse> created = new ArrayList<>();
        int aiAttempts = 0;
        int aiPicks = 0;
        boolean semanticPreferredFound = false;

        for (int activityIndex = 0; activityIndex < activities.size(); activityIndex++) {
            Activity activity = activities.get(activityIndex);
            if (created.size() >= MAX_SUGGESTIONS_PER_REPLAN) {
                break;
            }

            ZonedDateTime start = activity.getStartDateTime();
            ZonedDateTime end = activity.getEndDateTime();

            if (end != null && end.isBefore(now)) {
                continue;
            }
            if (start != null && start.isAfter(now.plusHours(WINDOW_HOURS))) {
                continue;
            }

            Location dropped = firstOutdoor(activity);
            if (dropped == null) {
                continue;
            }

            if (suggestionRepository.existsByActivityIdAndStatus(
                    activity.getId(), SuggestionStatus.PENDING)) {
                continue;
            }

            // Automated ticks must not nag about a stop the traveller already
            // rejected; the manual path stays free to ask again.
            if (trigger != TriggerSource.MANUAL
                    && suggestionRepository.existsByActivityIdAndStatus(
                            activity.getId(), SuggestionStatus.REJECTED)) {
                continue;
            }

            ZonedDateTime arrival = start != null && start.isAfter(now) ? start : now;
            ZonedDateTime fromWindow = start != null ? start : now;
            ZonedDateTime toWindow = end != null
                    ? end
                    : fromWindow.plusHours(RAIN_WINDOW_FALLBACK_HOURS);

            // Resolve per point, not per bean: the trip country only gates the
            // cheap early exit; the point decides which provider can answer.
            WeatherSource source = weatherSourceResolver
                    .forPoint(dropped.getLatitude(), dropped.getLongitude())
                    .orElse(null);

            if (source == null || !source.isRainy(
                    dropped.getLatitude(), dropped.getLongitude(), fromWindow, toWindow)) {
                continue;
            }

            Set<String> excluded = activity.getLocations().stream()
                    .map(Location::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> preferred = Set.of();
            PlaceDiscoveryService discovery = discoveryService.getIfAvailable();

            if (discovery != null && queryVector != null) {
                preferred = discovery.preferredIds(
                        dropped.getLatitude(),
                        dropped.getLongitude(),
                        CandidateFinder.RADIUS_KM,
                        queryVector,
                        SEMANTIC_SHORTLIST);
            }

            if (!preferred.isEmpty()) {
                semanticPreferredFound = true;
            }

            List<Candidate> candidates = candidateFinder.find(new CandidateRequest(
                    dropped.getId(), arrival, true, candidateInterests, SHORTLIST_LIMIT, excluded, preferred));

            if (candidates.isEmpty()) {
                log.info("No indoor candidate for activity id={} on trip id={}",
                        activity.getId(), tripId);
                continue;
            }

            String previousActivityName = activityIndex > 0
                    ? activities.get(activityIndex - 1).getName()
                    : null;

            String nextActivityName = activityIndex + 1 < activities.size()
                    ? activities.get(activityIndex + 1).getName()
                    : null;

            Set<String> candidateIds = candidates.stream()
                    .map(candidate -> candidate.location().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            PlanChoiceRequest choiceRequest = new PlanChoiceRequest(
                    "rain_expected",
                    activity.getName(),
                    dropped.getCategoryGroup(),
                    arrival,
                    interests,
                    previousActivityName,
                    nextActivityName,
                    tripGoal,
                    groupSummary,
                    candidates.stream()
                            .map(SuggestionService::toPlanCandidate)
                            .toList());

            Optional<PlanChoice> choice;
            aiAttempts++;
            try {
                choice = planChooser.choose(choiceRequest);
            } catch (RuntimeException e) {
                log.warn("Plan chooser failed for activity id={}: {}: {}",
                        activity.getId(), e.getClass().getSimpleName(), e.getMessage());
                choice = Optional.empty();
            }

            Candidate top = candidates.get(0);
            List<PlanChoice.RankedChoice> ranked =
                    PlanChoiceValidator.pickAll(choice.orElse(null), candidateIds, OPTION_LIMIT);

            List<SuggestionResponse.Option> options = new ArrayList<>(OPTION_LIMIT);
            Set<String> optionIds = new HashSet<>();

            for (PlanChoice.RankedChoice entry : ranked) {
                Candidate candidate = findCandidate(candidates, entry.id());
                if (candidate == null) {
                    continue;
                }
                Location location = candidate.location();
                String description = entry.description();
                if (description == null || description.isBlank()) {
                    description = fallbackDescription(location);
                }
                options.add(new SuggestionResponse.Option(
                        SuggestionResponse.ref(location),
                        options.size() + 1,
                        entry.reason(),
                        description,
                        true));
                optionIds.add(entry.id());
            }

            for (Candidate candidate : candidates) {
                if (options.size() >= OPTION_LIMIT) {
                    break;
                }
                String id = candidate.location().getId();
                if (id == null || !optionIds.add(id)) {
                    continue;
                }
                options.add(new SuggestionResponse.Option(
                        SuggestionResponse.ref(candidate.location()),
                        options.size() + 1,
                        fallbackReason(candidate),
                        fallbackDescription(candidate.location()),
                        false));
            }

            SuggestionResponse.Option first = options.get(0);
            Candidate firstCandidate = findCandidate(candidates, first.location().id());
            Location proposed = firstCandidate == null ? top.location() : firstCandidate.location();
            String explanation = first.reason();
            String aiChoiceId = first.aiPick() ? first.location().id() : null;
            if (first.aiPick()) {
                aiPicks++;
            }

            Suggestion suggestion = new Suggestion();
            suggestion.setId(ID.generateUniqueID(suggestionRepository::existsById));
            suggestion.setTrip(trip);
            suggestion.setActivity(activity);
            suggestion.setCurrentLocation(dropped);
            suggestion.setProposedLocation(proposed);
            suggestion.setStatus(SuggestionStatus.PENDING);
            suggestion.setReason("Rain is expected during " + activity.getName() + ".");
            suggestion.setExplanation(explanation);
            suggestion.setCandidatesJson(serialiseCandidates(candidates));
            suggestion.setOptionsJson(serialiseOptions(options));
            suggestion.setTopScoreChoiceId(top.location().getId());
            suggestion.setAiChoiceId(aiChoiceId);
            suggestion.setCreatedAt(Instant.now(clock));
            suggestion.setTriggerSource(trigger);

            SuggestionResponse response = SuggestionResponse.from(
                    suggestionRepository.save(suggestion));
            created.add(response);

            log.info("Created suggestion id={} for activity id={} on trip id={} chooser={}",
                    response.id(), activity.getId(), tripId,
                    first.aiPick() ? "LLM" : "FALLBACK");
        }

        log.info("replan_metrics trip={} suggestions={} ai_attempts={} ai_picks={} fallbacks={} retrieval={} latency_ms={}",
                tripId, created.size(), aiAttempts, aiPicks,
                created.size() - aiPicks,
                queryVector == null || !semanticPreferredFound ? "SQL_ONLY" : "SEMANTIC",
                (System.nanoTime() - startedAt) / 1_000_000);

        return created;
    }

    private Suggestion loadSuggestion(String suggestionId) {
        return suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> {
                    log.warn("Suggestion not found: id={}", suggestionId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Suggestion not found");
                });
    }

    private static void requirePending(Suggestion suggestion) {
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Suggestion is no longer pending");
        }
    }

    private static Location firstOutdoor(Activity activity) {
        return activity.getLocations().stream()
                .filter(Location::isOutdoor)
                .findFirst()
                .orElse(null);
    }

    private static int indexOf(List<Location> locations, Location target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < locations.size(); i++) {
            Location location = locations.get(i);
            if (location.getId() != null && location.getId().equals(target.getId())) {
                return i;
            }
        }
        return -1;
    }

    private static PlanChoiceRequest.PlanCandidate toPlanCandidate(Candidate candidate) {
        Location location = candidate.location();
        return new PlanChoiceRequest.PlanCandidate(
                location.getId(),
                location.getName(),
                location.getCategory(),
                location.getCategoryGroup(),
                location.getAddress(),
                location.getOpeningHours(),
                candidate.distanceKm(),
                candidate.openStatus().name(),
                "INDOOR".equalsIgnoreCase(location.getIndoorClass()),
                location.getEstCost());
    }

    private static Candidate findCandidate(List<Candidate> candidates, String id) {
        return candidates.stream()
                .filter(candidate -> Objects.equals(id, candidate.location().getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Why the candidate is a reasonable swap. Distance belongs here; the
     * description answers what the place actually is.
     */
    private static String fallbackReason(Candidate candidate) {
        StringBuilder reason = new StringBuilder("This is an indoor option about ")
                .append(String.format(Locale.ROOT, "%.1f", candidate.distanceKm()))
                .append(" km away");

        if (candidate.openStatus() == OpeningHours.Status.OPEN) {
            reason.append(", open at your arrival time");
        } else if (candidate.openStatus() == OpeningHours.Status.CLOSED) {
            reason.append(", but check its opening hours");
        }

        return reason.append(".").toString();
    }

    /**
     * What the place is, built only from fields we actually hold: category,
     * category group, address and opening hours. Never invents a specialty.
     */
    private static String fallbackDescription(Location location) {
        StringBuilder description = new StringBuilder(location.getName());
        String category = trimmedOrNull(location.getCategory());
        String group = trimmedOrNull(location.getCategoryGroup());

        if (category != null) {
            description.append(" is a ").append(category);
            if (group != null && !category.equalsIgnoreCase(group)) {
                description.append(" in the ").append(group).append(" category");
            }
        } else if (group != null) {
            description.append(" is a ").append(group).append(" place");
        } else {
            description.append(" is an indoor place");
        }

        String address = trimmedOrNull(location.getAddress());
        if (address != null) {
            description.append(" at ").append(address);
        }

        String hours = trimmedOrNull(location.getOpeningHours());
        if (hours != null) {
            description.append(", open ").append(hours);
        }

        return description.append(".").toString();
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String serialiseOptions(List<SuggestionResponse.Option> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise suggestion options", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not build suggestion");
        }
    }

    private String serialiseCandidates(List<Candidate> candidates) {
        List<Map<String, Object>> payload = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", candidate.location().getId());
            entry.put("name", candidate.location().getName());
            entry.put("score", candidate.score());
            entry.put("distanceKm", candidate.distanceKm());
            payload.add(entry);
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise suggestion candidates", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not build suggestion");
        }
    }
}
