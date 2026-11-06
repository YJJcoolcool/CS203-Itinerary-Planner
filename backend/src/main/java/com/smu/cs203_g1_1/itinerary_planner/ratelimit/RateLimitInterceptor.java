package com.smu.cs203_g1_1.itinerary_planner.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Applies per-user limits, keyed on the Clerk subject so no DB lookup is needed. */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final boolean enforceGeneral;

    public RateLimitInterceptor(RateLimitService rateLimitService,
                                @Value("${app.rate-limit.general.enforce}") boolean enforceGeneral) {
        this.rateLimitService = rateLimitService;
        this.enforceGeneral = enforceGeneral;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // With Clerk configured, security has already rejected anonymous /api requests.
        if (!(authentication instanceof JwtAuthenticationToken)) {
            return true;
        }
        String userId = authentication.getName();

        checkGeneral(userId, request);
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(AiRateLimited.class)) {
            checkAi(userId, request);
        }
        return true;
    }

    private void checkGeneral(String userId, HttpServletRequest request) {
        RateLimitDecision decision = rateLimitService.tryConsume(userId, RateLimitTier.GENERAL);
        if (decision.allowed()) {
            return;
        }
        log.warn("Rate limit exceeded tier=GENERAL enforced={} user={} method={} path={}",
                enforceGeneral, userId, request.getMethod(), request.getRequestURI());
        if (enforceGeneral) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }

    private void checkAi(String userId, HttpServletRequest request) {
        RateLimitDecision decision = rateLimitService.tryConsume(userId, RateLimitTier.AI);
        if (!decision.allowed()) {
            log.warn("Rate limit exceeded tier=AI enforced=true user={} method={} path={}",
                    userId, request.getMethod(), request.getRequestURI());
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
