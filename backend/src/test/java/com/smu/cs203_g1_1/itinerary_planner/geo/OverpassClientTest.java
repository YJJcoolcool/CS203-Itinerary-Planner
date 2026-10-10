package com.smu.cs203_g1_1.itinerary_planner.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.smu.cs203_g1_1.itinerary_planner.geo.overpass.OverpassElement;

class OverpassClientTest {

    private MockRestServiceServer server;
    private OverpassClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://overpass.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OverpassClient(builder.build());
    }

    @Test
    void queryPostsFormEncodedBodyAndMapsElements() {
        server.expect(requestTo(Matchers.startsWith("https://overpass.test")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("data=")))
                .andRespond(withSuccess("""
                        {"elements":[
                          {"type":"node","id":42,"lat":1.29,"lon":103.85,
                           "tags":{"name":"Test Cafe","amenity":"cafe"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<OverpassElement> elements = client.query("[out:json];node(1);out;");

        assertThat(elements).hasSize(1);
        OverpassElement element = elements.get(0);
        assertThat(element.type()).isEqualTo("node");
        assertThat(element.id()).isEqualTo(42L);
        assertThat(element.lat()).isEqualTo(1.29);
        assertThat(element.lon()).isEqualTo(103.85);
        assertThat(element.tags()).containsEntry("name", "Test Cafe").containsEntry("amenity", "cafe");
        server.verify();
    }

    @Test
    void queryWithServerErrorThrowsGeoUnavailable() {
        server.expect(requestTo(Matchers.startsWith("https://overpass.test")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.query("[out:json];node(1);out;"))
                .isInstanceOf(GeoUnavailableException.class);
        server.verify();
    }

    @Test
    void queryWithRateLimitStatusThrowsGeoUnavailable() {
        server.expect(requestTo(Matchers.startsWith("https://overpass.test")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.query("[out:json];node(1);out;"))
                .isInstanceOf(GeoUnavailableException.class);
        server.verify();
    }
}
