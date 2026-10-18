package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/four-day")
    public List<WeatherForecast> getFourDayForecast() {
        return weatherService.getFourDayForecast();
    }

    @GetMapping("/two-hour")
    public List<TwoHourWeatherForecast> getTwoHourForecast() {
        return weatherService.getTwoHourForecast();
    }
}
