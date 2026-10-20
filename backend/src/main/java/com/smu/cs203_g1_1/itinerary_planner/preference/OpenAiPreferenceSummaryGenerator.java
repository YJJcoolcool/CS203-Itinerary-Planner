package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * LLM-backed preference summariser.
 *
 * <p>Disabled when no API key is configured, and degrades to empty on any
 * provider failure so the caller can use the deterministic fallback.</p>
 */
@Service
public class OpenAiPreferenceSummaryGenerator implements PreferenceSummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPreferenceSummaryGenerator.class);

    private static final String NOT_CONFIGURED = "not-configured";

    private static final String SYSTEM_PROMPT = """
        You summarise travel preferences for an itinerary planner.
        Write at most three sentences of plain text.
        Describe the group's shared interests and constraints; do not invent facts.
        Never mention individual names, emails, or private notes.
        """;

    private final ChatClient chatClient;
    private final boolean configured;

    public OpenAiPreferenceSummaryGenerator(
            ChatClient.Builder builder,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatClient = builder.build();
        this.configured = apiKey != null && !apiKey.isBlank() && !NOT_CONFIGURED.equals(apiKey);
    }

    @Override
    public Optional<String> summariseGroup(List<UserPreference> members, TripPreference trip) {
        if (!configured || trip == null) {
            return Optional.empty();
        }

        StringBuilder payload = new StringBuilder();
        payload.append("Trip goal: ")
            .append(trip.getGoals().isEmpty() ? "none" : String.join(", ", trip.getGoals()))
            .append('\n');

        List<UserPreference> safeMembers = members == null ? List.of() : members;

        payload.append("Member preferences:\n");
        payload.append(safeMembers.stream()
            .map(OpenAiPreferenceSummaryGenerator::describe)
            .collect(Collectors.joining("\n")));

        return complete(payload.toString());
    }

    @Override
    public Optional<String> summariseUser(UserPreference preference) {
        if (!configured || preference == null) {
            return Optional.empty();
        }

        return complete("Traveller preferences:\n" + describe(preference));
    }

    private Optional<String> complete(String payload) {
        try {
            String summary = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(payload)
                .call()
                .content();

            if (summary == null || summary.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(summary.trim());
        } catch (RuntimeException ex) {
            log.warn("Preference summary failed, falling back to tags: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Renders one preference without exposing notes or identity. */
    private static String describe(UserPreference preference) {
        StringBuilder line = new StringBuilder("- interests: ")
            .append(preference.getTags().isEmpty()
                ? "none listed"
                : String.join(", ", preference.getTags()));

        if (preference.getMaxWalkKm() != null) {
            line.append("; max walk km: ").append(preference.getMaxWalkKm());
        }
        if (preference.getDietary() != null && !preference.getDietary().isBlank()) {
            line.append("; dietary: ").append(preference.getDietary());
        }
        if (preference.getIndoorPreference() != null && !preference.getIndoorPreference().isBlank()) {
            line.append("; indoor preference: ").append(preference.getIndoorPreference());
        }

        return line.toString();
    }
}
