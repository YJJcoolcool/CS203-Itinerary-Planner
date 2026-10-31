package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

/** Point-based daily weather outlook for trips in Open-Meteo regions. */
@RestController
@RequestMapping("/api/trips/{tripId}/weather")
public class TripWeatherOutlookController {

    private final WeatherOutlookService weatherOutlookService;
    private final TripService tripService;
    private final UserService userService;

    public TripWeatherOutlookController(WeatherOutlookService weatherOutlookService,
                                        TripService tripService,
                                        UserService userService) {
        this.weatherOutlookService = weatherOutlookService;
        this.tripService = tripService;
        this.userService = userService;
    }

    @GetMapping("/outlook")
    public List<DailyOutlook> outlook(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable String tripId) {
        Trip trip = tripService.getTripForMember(
            tripId, userService.getCurrentUserId(jwt));
        return weatherOutlookService.outlook(trip);
    }
}
