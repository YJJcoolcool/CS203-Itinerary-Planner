package com.smu.cs203_g1_1.itinerary_planner.routing;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.smu.cs203_g1_1.itinerary_planner.config.CacheConfig;

import com.smu.cs203_g1_1.itinerary_planner.routing.OneMapException;

@Component
public class OneMapClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String email;
    private final String password;

    private volatile String accessToken;
    private volatile Instant tokenExpiry;

    public OneMapClient(
            @Qualifier("oneMapRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.onemap.email}") String email,
            @Value("${app.onemap.password}") String password) {

        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.email = email;
        this.password = password;
    }

    /*
     * Obtain an access token.
     *
     * Reuse the existing token until it is close to expiry.
     */
    private synchronized String getAccessToken() {

        if (accessToken != null
                && tokenExpiry != null
                && Instant.now().isBefore(
                        tokenExpiry.minusSeconds(300))) {

            return accessToken;
        }

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "OneMap credentials are not configured");
        }

        TokenResponse response = restClient.post()
                .uri("/api/auth/post/getToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password))
                .retrieve()
                .body(TokenResponse.class);

        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()) {

            throw new IllegalStateException(
                    "Failed to obtain OneMap access token");
        }

        this.accessToken = response.accessToken();
        this.tokenExpiry = Instant.ofEpochSecond(
                response.expiryTimestamp());

        return accessToken;
    }

    /*
     * Invalidate the current token.
     */
    private synchronized void invalidateToken() {
        accessToken = null;
        tokenExpiry = null;
    }

    /*
     * Request a route from OneMap.
     */
    @Cacheable(
        cacheNames = CacheConfig.ROUTES,
        key = "#startLat + '|' + #startLon + '|' + #endLat + '|' + #endLon + '|' + #mode + '|' + #date + '|' + #time"
    )
    public JsonNode getRoute(
            double startLat,
            double startLon,
            double endLat,
            double endLon,
            RouteMode mode,
            String date,
            String time) {

        try {
            return requestRoute(
                    startLat, startLon,
                    endLat, endLon,
                    mode, date, time);

        } catch (HttpClientErrorException.Unauthorized ex) {

            // The token might have expired unexpectedly.
            invalidateToken();

            // Obtain a fresh token and retry once.
            try {
                return requestRoute(
                        startLat, startLon,
                        endLat, endLon,
                        mode, date, time);

            } catch (HttpClientErrorException.Unauthorized retryEx) {
                throw new OneMapException(
                        "OneMap authentication failed after refreshing the token",
                        HttpStatus.BAD_GATEWAY,
                        retryEx);
            }
        }
    }

    /*
     * Perform the actual HTTP request.
     */
    private JsonNode requestRoute(
            double startLat,
            double startLon,
            double endLat,
            double endLon,
            RouteMode mode,
            String date,
            String time) {
        
        String token = getAccessToken();
        
        try {
            String responseBody = restClient.get()
                .uri(uriBuilder -> {

                    var builder = uriBuilder
                            .path("/api/public/routingsvc/route")
                            .queryParam(
                                    "start",
                                    startLat + "," + startLon)
                            .queryParam(
                                    "end",
                                    endLat + "," + endLon)
                            .queryParam(
                                    "routeType",
                                    mode.getOneMapValue());

                    if (mode == RouteMode.PUBLIC_TRANSPORT) {

                        builder.queryParam("date", date);
                        builder.queryParam("time", time);
                        builder.queryParam("mode", "TRANSIT");
                        builder.queryParam("maxWalkDistance", 1000);
                        builder.queryParam("numItineraries", 3);
                    }

                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new OneMapException(
                        "OneMap returned an empty response",
                        HttpStatus.BAD_GATEWAY);
            }

            try {
                return objectMapper.readTree(responseBody);

            } catch (JacksonException e) {
                throw new OneMapException(
                        "Unable to parse OneMap routing response",
                        HttpStatus.BAD_GATEWAY,
                        e);
            }

        } catch (HttpClientErrorException.Unauthorized e) {

            // Let getRoute() handle token refresh and retry.
            throw e;

        } catch (RestClientResponseException e) {

            if (e.getStatusCode().value() == 429) {
                throw new OneMapException(
                        "OneMap rate limit exceeded. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        e);
            }

            throw new OneMapException(
                    "OneMap returned an error while calculating the route",
                    HttpStatus.BAD_GATEWAY,
                    e);

        } catch (ResourceAccessException e) {

            throw new OneMapException(
                    "Unable to connect to OneMap. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    e);
        }
    }

    /*
     * OneMap authentication response.
     */
    private record TokenResponse(

            @JsonProperty("access_token")
            String accessToken,

            @JsonProperty("expiry_timestamp")
            long expiryTimestamp

    ) {
    }
}