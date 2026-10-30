package com.smu.cs203_g1_1.itinerary_planner.routing.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OneMapClientConfig {

    @Bean(name = "oneMapRestClient")
    public RestClient oneMapRestClient(
            @Value("${app.onemap.base-url}") String baseUrl,
            @Value("${app.onemap.connect-timeout}") Duration connectTimeout,
            @Value("${app.onemap.read-timeout}") Duration readTimeout) {

        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(connectTimeout, readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(
                        ClientHttpRequestFactoryBuilder.detect()
                                .build(settings))
                .build();
    }
}