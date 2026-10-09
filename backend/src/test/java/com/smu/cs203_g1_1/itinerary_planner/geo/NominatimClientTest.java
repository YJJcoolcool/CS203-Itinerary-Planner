package com.smu.cs203_g1_1.itinerary_planner.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.smu.cs203_g1_1.itinerary_planner.geo.nominatim.NominatimPlace;

class NominatimClientTest {

    private MockRestServiceServer server;
    private NominatimClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://nominatim.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NominatimClient(builder.build());
    }

    @Test
    void searchMapsResultsAndSendsExpectedQuery() {
        server.expect(requestTo("https://nominatim.test/search"
                        + "?format=jsonv2&addressdetails=1&extratags=1&q=marina&limit=2&countrycodes=sg"))
                .andRespond(withSuccess("""
                        [
                          {"osm_type":"node","osm_id":123,"lat":"1.2834","lon":"103.8607",
                           "category":"tourism","type":"attraction",
                           "name":"Marina Bay Sands","display_name":"Marina Bay Sands, Singapore",
                           "importance":0.8}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<NominatimPlace> results = client.search("marina", 2, "sg", null, false);

        assertThat(results).hasSize(1);
        NominatimPlace place = results.get(0);
        assertThat(place.osmType()).isEqualTo("node");
        assertThat(place.osmId()).isEqualTo(123L);
        assertThat(Double.parseDouble(place.lat())).isEqualTo(1.2834);
        assertThat(Double.parseDouble(place.lon())).isEqualTo(103.8607);
        server.verify();
    }

    @Test
    void searchOmitsCountryCodesWhenBlank() {
        server.expect(requestTo("https://nominatim.test/search"
                        + "?format=jsonv2&addressdetails=1&extratags=1&q=marina&limit=2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<NominatimPlace> results = client.search("marina", 2, "  ", null, false);

        assertThat(results).isEmpty();
        server.verify();
    }

    @Test
    void reverseMapsPlace() {
        server.expect(requestTo("https://nominatim.test/reverse"
                        + "?format=jsonv2&addressdetails=1&extratags=1&lat=1.29&lon=103.85&zoom=18"))
                .andRespond(withSuccess("""
                        {"osm_type":"way","osm_id":55,"lat":"1.29","lon":"103.85",
                         "category":"building","type":"yes","name":"Somewhere",
                         "display_name":"Somewhere, Singapore"}
                        """, MediaType.APPLICATION_JSON));

        Optional<NominatimPlace> result = client.reverse(1.29, 103.85, 18);

        assertThat(result).isPresent();
        assertThat(result.get().osmType()).isEqualTo("way");
        assertThat(result.get().osmId()).isEqualTo(55L);
        server.verify();
    }

    @Test
    void reverseWithNullOsmTypeReturnsEmpty() {
        server.expect(requestTo("https://nominatim.test/reverse"
                        + "?format=jsonv2&addressdetails=1&extratags=1&lat=0.0&lon=0.0&zoom=18"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        Optional<NominatimPlace> result = client.reverse(0.0, 0.0, 18);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void lookupUsesOsmIdPrefixAndReturnsFirstElement() {
        server.expect(requestTo("https://nominatim.test/lookup"
                        + "?format=jsonv2&addressdetails=1&extratags=1&osm_ids=N123"))
                .andRespond(withSuccess("""
                        [{"osm_type":"node","osm_id":123,"lat":"1.0","lon":"2.0","name":"Test"}]
                        """, MediaType.APPLICATION_JSON));

        Optional<NominatimPlace> result = client.lookup("node", 123);

        assertThat(result).isPresent();
        assertThat(result.get().osmId()).isEqualTo(123L);
        server.verify();
    }

    @Test
    void lookupWithUnsupportedOsmTypeThrowsBadRequest() {
        assertThatThrownBy(() -> client.lookup("city", 1))
                .isInstanceOf(GeoBadRequestException.class);
    }

    @Test
    void searchUpstreamFailureThrowsGeoUnavailable() {
        server.expect(requestTo("https://nominatim.test/search"
                        + "?format=jsonv2&addressdetails=1&extratags=1&q=marina&limit=5"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.search("marina", 5, null, null, false))
                .isInstanceOf(GeoUnavailableException.class);
        server.verify();
    }
}
