package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionProfile;
import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.trips.Activity;
import com.smu.cs203_g1_1.itinerary_planner.trips.ActivityRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.weather.OpenMeteoForecast;
import com.smu.cs203_g1_1.itinerary_planner.weather.OpenMeteoWeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

/**
 * Daily, point-based outlook for destinations whose weather provider is
 * Open-Meteo. NEA has no point-based series, so SG keeps using its existing
 * panels and this service returns an empty list there.
 */
@Service
public class WeatherOutlookService {

    private static final String OPEN_METEO_ID = "open-meteo";
    private static final int MAX_FORECAST_DAYS = 14;

    private final ActivityRepository activityRepository;
    private final RegionRegistry regionRegistry;
    private final WeatherSourceResolver weatherSourceResolver;
    private final OpenMeteoWeatherSource openMeteoSource;
    private final Clock clock;

    @Autowired
    public WeatherOutlookService(
            ActivityRepository activityRepository,
            RegionRegistry regionRegistry,
            WeatherSourceResolver weatherSourceResolver,
            ObjectProvider<OpenMeteoWeatherSource> openMeteoSourceProvider,
            Clock clock) {
        this(activityRepository, regionRegistry, weatherSourceResolver,
            openMeteoSourceProvider.getIfAvailable(), clock);
    }

    WeatherOutlookService(
            ActivityRepository activityRepository,
            RegionRegistry regionRegistry,
            WeatherSourceResolver weatherSourceResolver,
            OpenMeteoWeatherSource openMeteoSource,
            Clock clock) {
        this.activityRepository = activityRepository;
        this.regionRegistry = regionRegistry;
        this.weatherSourceResolver = weatherSourceResolver;
        this.openMeteoSource = openMeteoSource;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DailyOutlook> outlook(Trip trip) {
        if (trip == null
                || trip.getStartDate() == null
                || trip.getEndDate() == null) {
            return List.of();
        }

        RegionProfile region = regionRegistry.byCode(trip.getCountryCode())
            .orElseGet(regionRegistry::fallback);
        if (region == null) {
            return List.of();
        }

        double[] point = firstLocatedPoint(trip.getId());
        if (point == null) {
            return List.of();
        }

        Optional<WeatherSource> provider = weatherSourceResolver.forPoint(
            point[0], point[1]);
        if (provider.isEmpty()
                || !OPEN_METEO_ID.equals(provider.get().id())
                || openMeteoSource == null) {
            return List.of();
        }

        Optional<OpenMeteoForecast> forecast = openMeteoSource.forecastForExact(
            point[0], point[1]);
        if (forecast.isEmpty()) {
            return List.of();
        }

        ZoneId zone = region.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = trip.getStartDate().isAfter(today) ? trip.getStartDate() : today;
        LocalDate lastForecastDay = today.plusDays(MAX_FORECAST_DAYS);
        LocalDate to = trip.getEndDate().isBefore(lastForecastDay)
            ? trip.getEndDate()
            : lastForecastDay;

        if (from.isAfter(to)) {
            return List.of();
        }

        Map<LocalDate, List<OpenMeteoForecast.Hour>> byDate = new TreeMap<>();
        for (OpenMeteoForecast.Hour hour : forecast.get().hours()) {
            if (hour == null) {
                continue;
            }
            LocalDate date = hour.time().atZone(zone).toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(hour);
        }

        return byDate.entrySet().stream()
            .map(entry -> summarize(entry.getKey(), entry.getValue()))
            .toList();
    }

    private double[] firstLocatedPoint(String tripId) {
        List<Activity> activities = activityRepository
            .findByTripIdOrderByStartDateTimeAsc(tripId);
        if (activities == null) {
            return null;
        }

        for (Activity activity : activities) {
            List<Location> locations = activity == null ? null : activity.getLocations();
            if (locations == null) {
                continue;
            }
            for (Location location : locations) {
                if (location != null
                        && location.getLatitude() != null
                        && location.getLongitude() != null) {
                    return new double[] {
                        location.getLatitude(),
                        location.getLongitude()
                    };
                }
            }
        }
        return null;
    }

    private static DailyOutlook summarize(LocalDate date,
                                          List<OpenMeteoForecast.Hour> hours) {
        double precipitationMm = 0;
        Integer probabilityPercent = null;
        Integer weatherCode = null;
        Double bestPrecipitation = null;
        boolean anyRainyHour = false;

        for (OpenMeteoForecast.Hour hour : hours) {
            if (hour.precipitationMm() != null) {
                precipitationMm += hour.precipitationMm();
            }

            if (hour.probabilityPercent() != null
                    && (probabilityPercent == null
                        || hour.probabilityPercent() > probabilityPercent)) {
                probabilityPercent = hour.probabilityPercent();
            }

            if (hour.weatherCode() != null
                    && (weatherCode == null
                        || (hour.precipitationMm() != null
                            && (bestPrecipitation == null
                                || hour.precipitationMm() > bestPrecipitation)))) {
                weatherCode = hour.weatherCode();
                bestPrecipitation = hour.precipitationMm();
            }

            anyRainyHour = anyRainyHour || OpenMeteoWeatherSource.isRainyHour(hour);
        }

        boolean rainy = anyRainyHour
            || OpenMeteoWeatherSource.isRainyTotal(precipitationMm);

        return new DailyOutlook(
            date, rainy, precipitationMm, probabilityPercent, weatherCode);
    }
}
