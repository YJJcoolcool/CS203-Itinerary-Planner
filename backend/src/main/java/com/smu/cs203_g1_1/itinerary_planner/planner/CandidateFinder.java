package com.smu.cs203_g1_1.itinerary_planner.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScore;
import com.smu.cs203_g1_1.itinerary_planner.trips.PlaceScoreRepository;

/**
 * Request-time candidate finding over the offline seeded places.
 *
 * <p>The pipeline is: bounding box -&gt; Haversine &lt;= 3 km -&gt; indoor
 * requirement -&gt; category compatibility -&gt; open at arrival -&gt;
 * exclusions -&gt; deterministic score/limit. It never calls OSM or any other
 * network service.</p>
 */
@Service
public class CandidateFinder {

    public static final double RADIUS_KM = 3.0;

    private static final double KM_PER_DEGREE_LAT = 111.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double MIN_COS_LAT = 1e-6;
    private static final double DEFAULT_QUALITY = 0.5;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final LocationRepository locationRepository;
    private final PlaceScoreRepository placeScoreRepository;
    private final ScoreCalculator scoreCalculator;

    @Autowired
    public CandidateFinder(LocationRepository locationRepository,
                           PlaceScoreRepository placeScoreRepository) {
        this(locationRepository, placeScoreRepository, new ScoreCalculator());
    }

    public CandidateFinder(LocationRepository locationRepository,
                           PlaceScoreRepository placeScoreRepository,
                           ScoreCalculator scoreCalculator) {
        this.locationRepository = Objects.requireNonNull(locationRepository);
        this.placeScoreRepository = Objects.requireNonNull(placeScoreRepository);
        this.scoreCalculator = Objects.requireNonNull(scoreCalculator);
    }

    public List<Candidate> find(CandidateRequest request) {
        validate(request);

        String droppedId = request.droppedLocationId();
        Location dropped = locationRepository.findById(droppedId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown dropped location: " + droppedId));

        double latitude = dropped.getLatitude();
        double longitude = dropped.getLongitude();

        double latDelta = RADIUS_KM / KM_PER_DEGREE_LAT;
        double cosLat = Math.cos(Math.toRadians(latitude));
        double lonDelta = Math.abs(cosLat) < MIN_COS_LAT
                ? 180.0
                : RADIUS_KM / (KM_PER_DEGREE_LAT * Math.abs(cosLat));

        List<Location> nearby = locationRepository.findSeededInBoundingBox(
                latitude - latDelta,
                latitude + latDelta,
                longitude - lonDelta,
                longitude + lonDelta);

        Set<String> excluded = request.excludedLocationIds() == null
                ? Set.of()
                : request.excludedLocationIds();

        String droppedGroup = dropped.getCategoryGroup();
        boolean knownDroppedGroup = CategorySimilarity.isKnownGroup(droppedGroup);

        List<Eligible> eligible = new ArrayList<>();

        for (Location candidate : nearby) {
            String candidateId = candidate.getId();
            if (candidateId == null
                    || candidateId.equals(droppedId)
                    || excluded.contains(candidateId)) {
                continue;
            }

            if (request.requireIndoor()
                    && !"INDOOR".equalsIgnoreCase(candidate.getIndoorClass())) {
                continue;
            }

            if (knownDroppedGroup
                    && CategorySimilarity.similarity(
                            candidate.getCategoryGroup(), droppedGroup) <= 0.0) {
                continue;
            }

            double distanceKm = haversineKm(
                    latitude, longitude,
                    candidate.getLatitude(), candidate.getLongitude());

            if (Double.isNaN(distanceKm) || distanceKm > RADIUS_KM) {
                continue;
            }

            OpeningHours.Status status =
                    OpeningHours.evaluate(candidate.getOpeningHours(), request.arrival());

            if (status == OpeningHours.Status.CLOSED) {
                continue;
            }

            eligible.add(new Eligible(
                    candidate,
                    distanceKm,
                    status,
                    status == OpeningHours.Status.UNKNOWN));
        }

        Map<String, Double> qualityById = loadManualQuality(eligible);

        List<Candidate> candidates = new ArrayList<>(eligible.size());
        for (Eligible item : eligible) {
            Location candidate = item.location();
            double quality = qualityById.getOrDefault(candidate.getId(), DEFAULT_QUALITY);
            double score = scoreCalculator.score(
                    candidate.getCategoryGroup(),
                    droppedGroup,
                    request.interests(),
                    item.distanceKm(),
                    candidate.getEstCost(),
                    item.unknownHours(),
                    quality);

            candidates.add(new Candidate(
                    candidate,
                    score,
                    item.distanceKm(),
                    item.status(),
                    item.unknownHours()));
        }

        candidates.sort(Comparator
                .comparingDouble(Candidate::score).reversed()
                .thenComparingDouble(Candidate::distanceKm)
                .thenComparing(candidate -> candidate.location().getId()));

        int size = Math.min(request.limit(), candidates.size());
        return List.copyOf(candidates.subList(0, size));
    }

    private Map<String, Double> loadManualQuality(List<Eligible> eligible) {
        if (eligible.isEmpty()) {
            return Map.of();
        }

        List<String> ids = eligible.stream()
                .map(item -> item.location().getId())
                .toList();

        Map<String, Double> qualityById = new HashMap<>();
        for (PlaceScore score : placeScoreRepository.findByLocationIdIn(ids)) {
            if ("MANUAL".equals(score.getSource()) && score.getQualityScore() != null) {
                qualityById.put(score.getLocationId(), score.getQualityScore().doubleValue());
            }
        }
        return qualityById;
    }

    private static void validate(CandidateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.droppedLocationId() == null
                || request.droppedLocationId().isBlank()) {
            throw new IllegalArgumentException("droppedLocationId must not be blank");
        }
        if (request.arrival() == null) {
            throw new IllegalArgumentException("arrival must not be null");
        }
        if (request.limit() < MIN_LIMIT || request.limit() > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT);
        }
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        return 2.0 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private record Eligible(Location location, double distanceKm,
                            OpeningHours.Status status, boolean unknownHours) {
    }
}
