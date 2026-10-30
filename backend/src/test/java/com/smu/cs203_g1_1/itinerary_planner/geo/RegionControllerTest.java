package com.smu.cs203_g1_1.itinerary_planner.geo;

import com.smu.cs203_g1_1.itinerary_planner.config.region.RegionRegistry;

import static org.hamcrest.Matchers.contains;
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
        RegionController controller = new RegionController(new RegionRegistry());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsConfiguredRegionsWithoutBoundingBoxes() throws Exception {
        mockMvc.perform(get("/api/regions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
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
            .andExpect(jsonPath("$[0].bboxes").doesNotExist());
    }
}
