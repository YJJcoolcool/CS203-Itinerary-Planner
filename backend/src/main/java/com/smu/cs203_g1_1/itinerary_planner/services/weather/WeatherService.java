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

    public List<WeatherForecast> getFourDayForecast() {
        return neaWeatherClient.getFourDayForecast()
            .stream()
            .sorted(Comparator.comparing(WeatherForecast::date))
            .toList();
    }

    public List<TwoHourWeatherForecast> getTwoHourForecast() {
        return neaWeatherClient.getTwoHourForecast();
    }
}
