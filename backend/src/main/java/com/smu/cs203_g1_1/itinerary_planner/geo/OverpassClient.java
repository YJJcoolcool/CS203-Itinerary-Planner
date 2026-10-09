package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.smu.cs203_g1_1.itinerary_planner.geo.overpass.OverpassElement;
import com.smu.cs203_g1_1.itinerary_planner.geo.overpass.OverpassResponse;

/**
 * Client for the Overpass API. Queries are POSTed as form-urlencoded Overpass QL
 * directly to the Overpass interpreter.
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);

    private final RestClient restClient;

    /**
     * @param restClient the Overpass-bound REST client
     */
    public OverpassClient(@Qualifier("overpassRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Runs an Overpass QL query.
     *
     * @param overpassQl Overpass QL; queries typically end with {@code out center;} so ways
     *                   and relations report their centre coordinates
     * @return the parsed elements, empty when the response carries none
     * @throws GeoUnavailableException if the upstream call fails
     */
    public List<OverpassElement> query(String overpassQl) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("data", overpassQl);
            OverpassResponse response = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OverpassResponse.class);
            if (response == null || response.elements() == null) {
                return List.of();
            }
            return response.elements();
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Overpass query failed: {}", e.getMessage());
            throw new GeoUnavailableException("POI service is temporarily unavailable");
        }
    }
}
