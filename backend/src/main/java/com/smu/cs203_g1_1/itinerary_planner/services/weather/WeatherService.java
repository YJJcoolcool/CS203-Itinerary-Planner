package com.smu.cs203_g1_1.itinerary_planner.services.weather;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class WeatherService {

    private final NEAWeatherClient neaWeatherClient;

    public WeatherService(NEAWeatherClient neaWeatherClient) {
        this.neaWeatherClient = neaWeatherClient;
    }

    public List<WeatherForecast> getForecast() {
        return neaWeatherClient.getForecast()
            .stream()
            .sorted(Comparator.comparing(WeatherForecast::date))
            .toList();
    }
}