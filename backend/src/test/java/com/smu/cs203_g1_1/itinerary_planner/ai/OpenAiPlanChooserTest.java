package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class OpenAiPlanChooserTest {

    private static final Resource SYSTEM_PROMPT =
            new ClassPathResource("prompts/choose-alternative.txt");

    @Mock
    private ChatClient.Builder builder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private PlanChoiceRequest request;

    @BeforeEach
    void setUp() {
        when(builder.build()).thenReturn(chatClient);

        request = new PlanChoiceRequest(
                "rain_expected",
                "Gardens by the Bay",
                "nature",
                ZonedDateTime.of(2026, 11, 2, 15, 0, 0, 0, ZoneId.of("Asia/Singapore")),
                Set.of("history"),
                "Lunch at Maxwell",
                "Dinner at Lau Pa Sat",
                "MUSEUMS, FOOD",
                "Quiet places, no long walks",
                List.of(new PlanChoiceRequest.PlanCandidate(
                        "Cult0001",
                        "National Museum",
                        "museum",
                        "culture",
                        0.8,
                        "OPEN",
                        true,
                        new BigDecimal("10.00"))));
    }

    @Test
    void unconfiguredKeyNeverCallsTheClient() {
        OpenAiPlanChooser chooser = new OpenAiPlanChooser(builder, "not-configured", SYSTEM_PROMPT);

        assertTrue(chooser.choose(request).isEmpty());
        verify(chatClient, never()).prompt();
    }

    @Test
    void blankKeyNeverCallsTheClient() {
        OpenAiPlanChooser chooser = new OpenAiPlanChooser(builder, "   ", SYSTEM_PROMPT);

        assertTrue(chooser.choose(request).isEmpty());
        verify(chatClient, never()).prompt();
    }

    @Test
    void configuredChooserReturnsTheParsedChoice() {
        PlanChoice expected = new PlanChoice(List.of(
                new PlanChoice.RankedChoice("Cult0001", 1, 0.9, "Matches history.", "Museum.")), false);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(PlanChoice.class)).thenReturn(expected);

        OpenAiPlanChooser chooser = new OpenAiPlanChooser(builder, "test-key", SYSTEM_PROMPT);

        Optional<PlanChoice> result = chooser.choose(request);

        assertTrue(result.isPresent());
        assertEquals(expected, result.get());

        ArgumentCaptor<String> userJson = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userJson.capture());
        assertTrue(userJson.getValue().contains("Cult0001"));
        assertTrue(userJson.getValue().contains("\"distance_km\""));
        assertFalse(userJson.getValue().contains("\"score\""));
        assertFalse(userJson.getValue().contains("\"rank\""));
    }

    @Test
    void clientFailureFallsBackToEmpty() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(PlanChoice.class)).thenThrow(new IllegalStateException("boom"));

        OpenAiPlanChooser chooser = new OpenAiPlanChooser(builder, "test-key", SYSTEM_PROMPT);

        assertTrue(chooser.choose(request).isEmpty());
    }
}
