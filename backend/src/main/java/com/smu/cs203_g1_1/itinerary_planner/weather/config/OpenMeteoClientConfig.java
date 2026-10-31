package com.smu.cs203_g1_1.itinerary_planner.weather.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Dedicated HTTP client for Open-Meteo. It must not inherit the much longer OSM
 * read timeout: a weather lookup is best-effort and belongs on a short leash.
 */
@Configuration
public class OpenMeteoClientConfig {

    @Bean
    public RestClient openMeteoRestClient(
            @Value("${app.weather.open-meteo.base-url:https://api.open-meteo.com}") String baseUrl,
            @Value("${app.weather.open-meteo.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.weather.open-meteo.read-timeout:8s}") Duration readTimeout) {

        HttpClientSettings settings = HttpClientSettings.defaults()
            .withTimeouts(connectTimeout, readTimeout);

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .build();
    }
}
