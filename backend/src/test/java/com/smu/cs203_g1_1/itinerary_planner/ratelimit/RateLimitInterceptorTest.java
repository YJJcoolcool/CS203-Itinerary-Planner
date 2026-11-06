package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitInterceptorTest {

    private final RateLimitService service = mock(RateLimitService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trips");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enforcedGeneralLimitRejectsWithTheWaitTime() {
        signIn();
        when(service.tryConsume("user", RateLimitTier.GENERAL)).thenReturn(new RateLimitDecision(false, 7));
        RateLimitInterceptor interceptor = new RateLimitInterceptor(service, true);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(7));
    }

    @Test
    void requestsWithoutAJwtAreNotCounted() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("anon", "x"));
        RateLimitInterceptor interceptor = new RateLimitInterceptor(service, true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void waitMessageSwitchesToMinutesForLongWaits() {
        assertThat(new RateLimitExceededException(1).getMessage()).contains("in 1 second.");
        assertThat(new RateLimitExceededException(2880).getMessage()).contains("in 48 minutes.");
    }

    private static void signIn() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", "user"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
