package com.smu.cs203_g1_1.itinerary_planner.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring AI chooser backed by OpenAI.
 *
 * <p>The model only re-ranks the closed candidate set we send. It never gets
 * our scores, ranking order or raw OSM tags, and its output is validated
 * against the offered ids before use. When no API key is configured, or the
 * call fails for any reason, this chooser returns empty so the caller can fall
 * back to its deterministic ranking.</p>
 */
@Service
public class OpenAiPlanChooser implements PlanChooser {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPlanChooser.class);

    private static final String NOT_CONFIGURED = "not-configured";

    private final ChatClient chatClient;
    private final String systemPromptText;
    private final boolean configured;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiPlanChooser(
            ChatClient.Builder builder,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("classpath:prompts/choose-alternative.txt") Resource systemPrompt) {
        this.chatClient = builder.build();
        this.configured = isConfigured(apiKey);
        this.systemPromptText = loadPrompt(systemPrompt);
    }

    @Override
    public Optional<PlanChoice> choose(PlanChoiceRequest request) {
        if (!configured || request == null
                || request.candidates() == null || request.candidates().isEmpty()) {
            return Optional.empty();
        }

        String userJson;
        try {
            userJson = objectMapper.writeValueAsString(toPayload(request));
        } catch (JsonProcessingException e) {
            log.warn("Plan chooser could not serialise {} candidates: {}: {}",
                    request.candidates().size(),
                    e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }

        try {
            PlanChoice choice = chatClient.prompt()
                    .system(systemPromptText)
                    .user(userJson)
                    .call()
                    .entity(PlanChoice.class);

            if (choice == null) {
                log.debug("Plan chooser returned no choice for {} candidates",
                        request.candidates().size());
                return Optional.empty();
            }

            int count = choice.ranking() == null ? 0 : choice.ranking().size();
            log.debug("Plan chooser returned {} ranked choices for {} candidates",
                    count, request.candidates().size());
            return Optional.of(choice);
        } catch (RuntimeException e) {
            log.warn("Plan chooser failed for {} candidates: {}: {}",
                    request.candidates().size(),
                    e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> toPayload(PlanChoiceRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trigger", request.triggerReason());

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("name", request.activityName());
        activity.put("category_group", request.activityCategoryGroup());
        payload.put("activity", activity);

        payload.put("arrival", request.arrival() == null ? null : request.arrival().toString());

        Set<String> interests = request.interests() == null ? Set.of() : request.interests();
        payload.put("interests", new ArrayList<>(interests));

        payload.put("previous_activity", request.previousActivityName());
        payload.put("next_activity", request.nextActivityName());
        payload.put("trip_goal", request.tripGoal());
        payload.put("group_summary", request.groupSummary());

        // Shuffle a copy so position does not bias the model. Scores and our
        // ranking order stay internal.
        List<PlanChoiceRequest.PlanCandidate> offered =
                new ArrayList<>(request.candidates());
        Collections.shuffle(offered);

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (PlanChoiceRequest.PlanCandidate candidate : offered) {
            if (candidate == null) {
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", candidate.id());
            entry.put("name", candidate.name());
            entry.put("category", candidate.category());
            entry.put("category_group", candidate.categoryGroup());
            entry.put("address", candidate.address());
            entry.put("opening_hours", candidate.openingHours());
            entry.put("distance_km", candidate.distanceKm());
            entry.put("opening_status", candidate.openingStatus());
            entry.put("indoor", candidate.indoor());
            entry.put("est_cost", candidate.estCost());
            candidates.add(entry);
        }
        payload.put("candidates", candidates);

        return payload;
    }

    private static boolean isConfigured(String apiKey) {
        return apiKey != null
                && !apiKey.isBlank()
                && !NOT_CONFIGURED.equalsIgnoreCase(apiKey.trim());
    }

    private static String loadPrompt(Resource systemPrompt) {
        try {
            return systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the plan chooser system prompt", e);
        }
    }
}
