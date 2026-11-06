package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@ExtendWith(MockitoExtension.class)
class SuggestionControllerTest {

    @Mock
    private SuggestionService suggestionService;

    @Mock
    private UserService userService;

    @Mock
    private Jwt jwt;

    private SuggestionController controller;

    @BeforeEach
    void setUp() {
        controller = new SuggestionController(suggestionService, userService);
    }

    @Test
    void listUsesResolvedUserId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(suggestionService.list("Trip0001", "User0001")).thenReturn(List.of());

        assertEquals(List.of(), controller.list(jwt, "Trip0001"));
        verify(suggestionService).list("Trip0001", "User0001");
    }

    @Test
    void acceptUsesResolvedUserId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");

        controller.accept(jwt, "Sugg0001", null);

        verify(suggestionService).accept("Sugg0001", "User0001", null);
    }

    @Test
    void acceptForwardsTheRequestedLocationId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");

        controller.accept(jwt, "Sugg0001", new SuggestionController.AcceptRequest("Loc00001"));

        verify(suggestionService).accept("Sugg0001", "User0001", "Loc00001");
    }

    @Test
    void rejectUsesResolvedUserId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");

        controller.reject(jwt, "Sugg0001");

        verify(suggestionService).reject("Sugg0001", "User0001");
    }

    @Test
    void replanPassesNullWhenBodyIsMissing() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(suggestionService.replan("Trip0001", "User0001", null)).thenReturn(List.of());

        assertEquals(List.of(), controller.replan(jwt, "Trip0001", null));
        verify(suggestionService).replan("Trip0001", "User0001", null);
    }

    @Test
    void replanPassesAtWhenBodyIsProvided() {
        ZonedDateTime at = ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0,
                ZoneId.of("Asia/Singapore"));
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(suggestionService.replan("Trip0001", "User0001", at)).thenReturn(List.of());

        assertEquals(List.of(),
                controller.replan(jwt, "Trip0001", new SuggestionController.ReplanRequest(at)));
        verify(suggestionService).replan("Trip0001", "User0001", at);
    }
}
