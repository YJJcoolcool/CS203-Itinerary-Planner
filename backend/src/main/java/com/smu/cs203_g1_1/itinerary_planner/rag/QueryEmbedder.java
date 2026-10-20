package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Embeds the retrieval query, caching by text.
 *
 * <p>Failures are swallowed on purpose: semantic retrieval is an enrichment. If
 * the embedding call fails, discovery falls back to the SQL arm and the replan
 * still completes.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class QueryEmbedder {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbedder.class);

    private final EmbeddingModel embeddingModel;
    private final Cache<String, float[]> cache;

    public QueryEmbedder(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    }

    /** Returns the query vector, or empty when embedding is unavailable. */
    public Optional<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        float[] cached = cache.getIfPresent(text);

        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            float[] vector = embeddingModel.embed(text);

            if (vector == null || vector.length == 0) {
                return Optional.empty();
            }

            cache.put(text, vector);

            return Optional.of(vector);
        } catch (RuntimeException ex) {
            log.warn("Query embedding failed, falling back to SQL-only discovery: {}",
                ex.getMessage());

            return Optional.empty();
        }
    }

    /** Convenience for the replan path. */
    public Optional<float[]> embedQuery(String tripGoal,
                                        String groupSummary,
                                        java.util.Set<String> travellerTags,
                                        String weatherNote) {
        return embed(QueryTextBuilder.build(tripGoal, groupSummary, travellerTags, weatherNote));
    }
}
