package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.smu.cs203_g1_1.itinerary_planner.preference.PreferenceService;
import com.smu.cs203_g1_1.itinerary_planner.preference.UserPreference;
import com.smu.cs203_g1_1.itinerary_planner.suggestion.SuggestionService;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

/** Uses the real Spring MVC wiring, so it fails if the interceptor or handler isn't registered. */
@SpringBootTest(properties = {
        "CLERK_ISSUER_URI=",
        "app.polling.enabled=false",
        "app.rate-limit.general.enforce=false",
        "app.rate-limit.general.per-minute=3",
        "app.rate-limit.ai.per-minute=2",
        "app.rate-limit.ai.per-day=30"})
class RateLimitIntegrationTest {

    private static final String REPLAN = "/api/trips/Trip0001/replan";

    @Autowired
    private WebApplicationContext context;
    @MockitoBean
    private SuggestionService suggestionService;
    @MockitoBean
    private PreferenceService preferenceService;
    @MockitoBean
    private UserService userService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(suggestionService.replan(any(), any(), any())).thenReturn(List.of());
        when(suggestionService.list(any(), any())).thenReturn(List.of());
        when(preferenceService.summariseUser(any())).thenReturn(mock(UserPreference.class));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aiEndpointReturns429WithRetryAfterOnceOverLimit() throws Exception {
        signInAs("over-limit-user");
        mvc.perform(post(REPLAN)).andExpect(status().isOk());
        mvc.perform(post(REPLAN)).andExpect(status().isOk());

        mvc.perform(post(REPLAN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.message")
                        .value("Too many requests. Please try again in 30 seconds."));
    }

    @Test
    void aiEndpointsShareOneBucket() throws Exception {
        signInAs("shared-bucket-user");
        mvc.perform(post(REPLAN)).andExpect(status().isOk());
        mvc.perform(post("/api/users/me/preferences/summarise")).andExpect(status().isOk());

        mvc.perform(post(REPLAN)).andExpect(status().isTooManyRequests());
    }

    @Test
    void oneUsersLimitDoesNotAffectAnother() throws Exception {
        signInAs("first-user");
        mvc.perform(post(REPLAN));
        mvc.perform(post(REPLAN));

        signInAs("second-user");
        mvc.perform(post(REPLAN)).andExpect(status().isOk());
    }

    @Test
    void generalLimitOnlyLogsWhileNotEnforced() throws Exception {
        signInAs("log-only-user");
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/trips/Trip0001/suggestions")).andExpect(status().isOk());
        }
    }

    private static void signInAs(String subject) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", subject));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
