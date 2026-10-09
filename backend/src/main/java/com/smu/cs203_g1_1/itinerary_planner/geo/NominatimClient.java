package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.smu.cs203_g1_1.itinerary_planner.geo.nominatim.NominatimPlace;

/**
 * Thin client over the Nominatim API for forward geocoding, reverse geocoding, and
 * OSM-id lookup. Calls go directly to the Nominatim REST client, and upstream
 * HTTP/connection failures surface as {@link GeoUnavailableException}.
 */
@Component
public class NominatimClient {

    private final RestClient restClient;

    /**
     * @param restClient the Nominatim-bound REST client
     */
    public NominatimClient(@Qualifier("nominatimRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Forward-geocodes a free-text query.
     *
     * @param query        search text
     * @param limit        maximum number of results to request
     * @param countryCodes optional comma-separated ISO country codes, sent only when non-blank
     * @param viewBox      optional bounding box bias, sent only when non-blank
     * @param bounded      whether to restrict results to {@code viewBox}
     * @return matching places, empty when the body is absent
     * @throws GeoUnavailableException on upstream HTTP or connection failure
     */
    public List<NominatimPlace> search(String query, int limit, String countryCodes, String viewBox, boolean bounded) {
        try {
            NominatimPlace[] body = restClient.get()
                    .uri(uriBuilder -> { uriBuilder.path("/search")
                                .queryParam("format", "jsonv2")
                                .queryParam("addressdetails", 1)
                                .queryParam("extratags", 1)
                                .queryParam("q", query)
                                .queryParam("limit", limit);
                        if (countryCodes != null && !countryCodes.isBlank()) {
                            uriBuilder.queryParam("countrycodes", countryCodes);
                        }
                        if (viewBox != null && !viewBox.isBlank()) {
                            uriBuilder.queryParam("viewbox", viewBox);
                            uriBuilder.queryParam("bounded", bounded ? 1 : 0);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(NominatimPlace[].class);
            return body == null ? List.of() : List.of(body);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new GeoUnavailableException("Geocoding service is temporarily unavailable");
        }
    }

    /**
     * Reverse-geocodes coordinates to the nearest place.
     *
     * @param lat  latitude
     * @param lon  longitude
     * @param zoom Nominatim zoom level
     * @return the place, or empty when Nominatim returns its HTTP 200 {@code {"error":...}}
     *         body (which has a null {@code osm_type})
     * @throws GeoUnavailableException on upstream HTTP or connection failure
     */
    public Optional<NominatimPlace> reverse(double lat, double lon, int zoom) {
        try {
            NominatimPlace body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("extratags", 1)
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("zoom", zoom)
                            .build())
                    .retrieve()
                    .body(NominatimPlace.class);
            if (body == null || body.osmType() == null) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new GeoUnavailableException("Geocoding service is temporarily unavailable");
        }
    }

    /**
     * Looks up a single place by OSM identity, translating {@code osmType} to its Nominatim
     * prefix (node→N, way→W, relation→R).
     *
     * @param osmType one of node/way/relation, case-insensitive
     * @param osmId   OSM numeric id
     * @return the first matching place, or empty when none is found
     * @throws GeoBadRequestException  if {@code osmType} is not a supported type
     * @throws GeoUnavailableException on upstream HTTP or connection failure
     */
    public Optional<NominatimPlace> lookup(String osmType, long osmId) {
        String prefix;
        if ("node".equalsIgnoreCase(osmType)) {
            prefix = "N";
        } else if ("way".equalsIgnoreCase(osmType)) {
            prefix = "W";
        } else if ("relation".equalsIgnoreCase(osmType)) {
            prefix = "R";
        } else {
            throw new GeoBadRequestException("Unsupported OSM type: " + osmType);
        }

        try {
            NominatimPlace[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/lookup")
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("extratags", 1)
                            .queryParam("osm_ids", prefix + osmId)
                            .build())
                    .retrieve()
                    .body(NominatimPlace[].class);
            if (body == null || body.length == 0) {
                return Optional.empty();
            }
            return Optional.of(body[0]);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new GeoUnavailableException("Geocoding service is temporarily unavailable");
        }
    }
}
