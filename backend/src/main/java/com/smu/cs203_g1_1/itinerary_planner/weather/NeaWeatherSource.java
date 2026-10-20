package com.smu.cs203_g1_1.itinerary_planner.weather;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smu.cs203_g1_1.itinerary_planner.services.weather.NEAWeatherClient;
import com.smu.cs203_g1_1.itinerary_planner.services.weather.TwoHourWeatherForecast;
import com.smu.cs203_g1_1.itinerary_planner.services.weather.WeatherForecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Real NEA-backed {@link WeatherSource}, scoped to Singapore.
 *
 * <p>NEA's two-hour forecast is keyed by area name, not coordinates, so the
 * classpath resource {@code nea/area-centroids.json} (generated from the same
 * feed's {@code area_metadata}) maps a coordinate to the nearest forecast area.
 * The feed is chosen by window length alone:</p>
 *
 * <ul>
 *   <li>{@code <= 2 h}: only the area-level two-hour feed. If that area has no
 *       forecast overlapping the window, the answer is {@code false} (an
 *       uncovered window is unknown, never assumed wet).</li>
 *   <li>{@code > 2 h}: only the four-day outlook. The two-hour feed is not
 *       fetched at all.</li>
 * </ul>
 *
 * <p><strong>Known limitation of the four-day path:</strong> the data.gov.sg
 * four-day endpoint
 * ({@code https://api-open.data.gov.sg/v2/real-time/api/four-day-outlook})
 * publishes a national daily outlook keyed by date only. It contains no
 * North/South/East/West/Central region breakdown, and no region mapping is
 * available from this endpoint. The long-window answer is therefore
 * deliberately coarse and conservative: it reports rain if any forecast day
 * overlapping the window is wet nationally. It must never be used for short,
 * imminent windows; those always go through the area-level two-hour feed.</p>
 *
 * <p>Every failure path returns {@code false}: a point outside Singapore, an
 * area missing from the feed, an expired feed, or an upstream error can only
 * suppress a suggestion, never break a replan.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.weather.source", havingValue = "nea")
public class NeaWeatherSource implements WeatherSource {

    private static final Logger log = LoggerFactory.getLogger(NeaWeatherSource.class);

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private static final String AREA_RESOURCE = "nea/area-centroids.json";

    /** Singapore bounding box; points outside it are never evaluated. */
    static final double MIN_LATITUDE = 1.15;
    static final double MAX_LATITUDE = 1.48;
    static final double MIN_LONGITUDE = 103.60;
    static final double MAX_LONGITUDE = 104.10;

    /** The two-hour feed is only meaningful for windows up to this length. */
    private static final Duration TWO_HOUR_WINDOW = Duration.ofHours(2);

    private final NEAWeatherClient client;
    private final Clock clock;
    private final List<Area> areas;

    /**
     * Marks this constructor for injection: the class also has a package-private
     * constructor for tests, so Spring cannot infer which one to use.
     */
    @Autowired
    public NeaWeatherSource(NEAWeatherClient client) {
        this(client, loadAreas(), Clock.system(SINGAPORE));
    }

    NeaWeatherSource(NEAWeatherClient client, List<Area> areas, Clock clock) {
        if (areas == null || areas.isEmpty()) {
            throw new IllegalArgumentException("At least one NEA area is required");
        }

        this.client = client;
        this.areas = List.copyOf(areas);
        this.clock = clock == null ? Clock.system(SINGAPORE) : clock;
    }

    @Override
    public boolean isRainy(double latitude, double longitude,
                           ZonedDateTime from, ZonedDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            return false;
        }

        if (!isInSingapore(latitude, longitude)) {
            return false;
        }

        Area area = nearestArea(latitude, longitude);

        try {
            // The feed is selected by window length only: the four-day outlook
            // exists precisely for windows the two-hour feed does not cover.
            if (Duration.between(from, to).compareTo(TWO_HOUR_WINDOW) <= 0) {
                return overlappingTwoHour(area, from, to).stream()
                    .anyMatch(TwoHourWeatherForecast::rainExpected);
            }

            return fourDayHintsRain(from, to);
        } catch (RuntimeException ex) {
            log.warn("NEA forecast unavailable, treating as no rain: {}", ex.getMessage());
            return false;
        }
    }

    private List<TwoHourWeatherForecast> overlappingTwoHour(Area area,
                                                             ZonedDateTime from,
                                                             ZonedDateTime to) {
        List<TwoHourWeatherForecast> forecasts = client.getTwoHourForecast();

        if (forecasts == null) {
            return List.of();
        }

        return forecasts.stream()
            .filter(forecast -> forecast.area() != null
                && forecast.area().equalsIgnoreCase(area.name()))
            .filter(forecast -> overlaps(forecast.validFrom(), forecast.validUntil(), from, to))
            .toList();
    }

    /**
     * Coarse, national answer for windows longer than two hours. The published
     * feed has no regional breakdown, so any wet day overlapping the window
     * counts as rainy. Never used for short, imminent windows.
     */
    private boolean fourDayHintsRain(ZonedDateTime from, ZonedDateTime to) {
        List<WeatherForecast> forecasts = client.getFourDayForecast();

        if (forecasts == null) {
            return false;
        }

        LocalDate first = from.withZoneSameInstant(SINGAPORE).toLocalDate();
        LocalDate last = to.withZoneSameInstant(SINGAPORE).toLocalDate();

        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            LocalDate current = day;

            boolean rainy = forecasts.stream()
                .anyMatch(forecast -> current.equals(forecast.date())
                    && forecast.rainExpected());

            if (rainy) {
                return true;
            }
        }

        return false;
    }

    private static boolean overlaps(OffsetDateTime forecastStart,
                                    OffsetDateTime forecastEnd,
                                    ZonedDateTime windowStart,
                                    ZonedDateTime windowEnd) {
        if (forecastStart == null || forecastEnd == null) {
            return false;
        }

        OffsetDateTime start = windowStart.toOffsetDateTime();
        OffsetDateTime end = windowEnd.toOffsetDateTime();

        return !end.isBefore(forecastStart) && !start.isAfter(forecastEnd);
    }

    static boolean isInSingapore(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return false;
        }

        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
            && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    Area nearestArea(double latitude, double longitude) {
        Area nearest = areas.get(0);
        double best = Double.MAX_VALUE;

        for (Area area : areas) {
            double distance = distanceKm(latitude, longitude, area.latitude(), area.longitude());

            if (distance < best) {
                best = distance;
                nearest = area;
            }
        }

        return nearest;
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.pow(Math.sin(dLon / 2), 2);

        return 2 * 6371.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private static List<Area> loadAreas() {
        ClassPathResource resource = new ClassPathResource(AREA_RESOURCE);

        try (InputStream input = resource.getInputStream()) {
            AreaFile file = new ObjectMapper().readValue(input, AreaFile.class);

            if (file == null || file.areas() == null || file.areas().isEmpty()) {
                throw new IllegalStateException("'" + AREA_RESOURCE + "' contains no areas");
            }

            return file.areas();
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Unable to load NEA area centroids from '" + AREA_RESOURCE + "'", ex);
        }
    }

    /** One NEA two-hour forecast area and the point NEA publishes for it. */
    public record Area(String name, double latitude, double longitude) {}

    private record AreaFile(String source, String retrieved, List<Area> areas) {}
}
