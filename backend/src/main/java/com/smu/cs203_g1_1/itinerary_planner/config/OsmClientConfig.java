package com.smu.cs203_g1_1.itinerary_planner.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Configures the two OpenStreetMap HTTP clients (Nominatim and Overpass) with their base
 * URL, the required User-Agent header, and connect/read timeouts.
 */
@Configuration
public class OsmClientConfig {

    /**
     * Builds the Nominatim client from {@code app.osm.nominatim.base-url} with the shared
     * User-Agent (required by OSM policy) and configured timeouts.
     */
    @Bean
    public RestClient nominatimRestClient(
            @Value("${app.osm.nominatim.base-url}") String baseUrl,
            @Value("${app.osm.user-agent}") String userAgent,
            @Value("${app.osm.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.osm.read-timeout:30s}") Duration readTimeout) {
        return buildClient(baseUrl, userAgent, connectTimeout, readTimeout);
    }

    /**
     * Builds the Overpass client from {@code app.osm.overpass.base-url} with the shared
     * User-Agent and configured timeouts.
     */
    @Bean
    public RestClient overpassRestClient(
            @Value("${app.osm.overpass.base-url}") String baseUrl,
            @Value("${app.osm.user-agent}") String userAgent,
            @Value("${app.osm.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.osm.read-timeout:30s}") Duration readTimeout) {
        return buildClient(baseUrl, userAgent, connectTimeout, readTimeout);
    }

    /**
     * Assembles a {@link RestClient} with the given base URL, User-Agent header, and HTTP timeouts.
     *
     * @param baseUrl        service root (Nominatim or Overpass endpoint)
     * @param userAgent      User-Agent sent on every request, required by OSM usage policy
     * @param connectTimeout socket connect timeout
     * @param readTimeout    response read timeout
     * @return the configured client
     */
    private static RestClient buildClient(String baseUrl, String userAgent,
            Duration connectTimeout, Duration readTimeout) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(connectTimeout, readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
