package com.smu.cs203_g1_1.itinerary_planner.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the place-embedding job on startup when
 * {@code app.rag.embed-on-startup=true}.
 *
 * <p>Off by default: embedding ~10k places costs a provider round trip per
 * batch and should be an explicit operator action, not something every local
 * boot repeats. Failures are logged; the application still starts.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "embed-on-startup", havingValue = "true")
public class PlaceEmbeddingRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceEmbeddingRunner.class);

    private final PlaceEmbedder embedder;
    private final String model;

    public PlaceEmbeddingRunner(PlaceEmbedder embedder,
                                @Value("${app.rag.model:text-embedding-3-small}") String model) {
        this.embedder = embedder;
        this.model = model;
    }

    @Override
    public void run(String... args) {
        try {
            embedder.embedMissing(model);
        } catch (RuntimeException ex) {
            log.error("Place embedding failed ({}); semantic ranking will use whatever is stored",
                ex.getMessage());
        }
    }
}
