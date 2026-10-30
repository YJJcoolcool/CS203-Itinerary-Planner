package com.smu.cs203_g1_1.itinerary_planner.geo;

import java.util.List;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;
import com.smu.cs203_g1_1.itinerary_planner.weather.DefaultWeatherSourceResolver;
import com.smu.cs203_g1_1.itinerary_planner.weather.ProgrammableWeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSource;
import com.smu.cs203_g1_1.itinerary_planner.weather.WeatherSourceResolver;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RegionRegistry registry = new RegionRegistry();
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            registry, List.of(
                new ProgrammableWeatherSource("nea"),
                new ProgrammableWeatherSource("open-meteo"),
                new ProgrammableWeatherSource("none")));
        mockMvc = MockMvcBuilders.standaloneSetup(
            new RegionController(registry, resolver)).build();
    }

    @Test
    void returnsConfiguredRegionsAndTheFallbackWithoutBoundingBoxes() throws Exception {
        mockMvc.perform(get("/api/regions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].code").value("sg"))
            .andExpect(jsonPath("$[0].name").value("Singapore"))
            .andExpect(jsonPath("$[0].tier").value("full"))
            .andExpect(jsonPath("$[0].locale").value("en-SG"))
            .andExpect(jsonPath("$[0].currency").value("SGD"))
            .andExpect(jsonPath("$[0].defaultZone").value("Asia/Singapore"))
            .andExpect(jsonPath("$[0].capabilities", contains("weather", "routing")))
            .andExpect(jsonPath("$[0].tiles.crs").value("wgs84"))
            .andExpect(jsonPath("$[0].tiles.url")
                .value("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"))
            .andExpect(jsonPath("$[0].tiles.attribution")
                .value("© OpenStreetMap contributors"))
            .andExpect(jsonPath("$[0].bboxes").doesNotExist())
            .andExpect(jsonPath("$[1].code").value("*"))
            .andExpect(jsonPath("$[1].tier").value("partial"))
            .andExpect(jsonPath("$[1].defaultZone").value("UTC"))
            .andExpect(jsonPath("$[1].capabilities", contains("weather", "forecast")))
            .andExpect(jsonPath("$[1].tiles.crs").value("wgs84"))
            .andExpect(jsonPath("$[1].bboxes").doesNotExist());
    }

    @Test
    void fixtureOnlyRuntimeAdvertisesWeatherButNotForecast() throws Exception {
        RegionRegistry registry = new RegionRegistry();
        WeatherSource fixture = new ProgrammableWeatherSource("fixture");
        WeatherSourceResolver resolver = new DefaultWeatherSourceResolver(
            registry, List.of(fixture));

        assertSame(fixture, resolver.forRegion(registry.fallback()).orElseThrow());

        MockMvc fixtureMvc = MockMvcBuilders.standaloneSetup(
            new RegionController(registry, resolver)).build();

        fixtureMvc.perform(get("/api/regions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].capabilities", contains("weather")));
    }
}
