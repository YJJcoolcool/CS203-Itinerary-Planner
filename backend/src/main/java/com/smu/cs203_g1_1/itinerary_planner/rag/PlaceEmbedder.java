package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fills {@code place_embedding} for locations that do not have a vector yet.
 *
 * <p>One-off enrichment: batching keeps the provider call count low (~100 texts
 * per request), and a failed batch is logged and skipped rather than aborting
 * the whole run. Re-running is cheap because embedded ids are filtered out
 * first.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class PlaceEmbedder {

    private static final Logger log = LoggerFactory.getLogger(PlaceEmbedder.class);

    static final int DEFAULT_BATCH_SIZE = 100;

    private final LocationRepository locations;
    private final PlaceEmbeddingRepository embeddings;
    private final EmbeddingModel embeddingModel;

    public PlaceEmbedder(LocationRepository locations,
                         PlaceEmbeddingRepository embeddings,
                         EmbeddingModel embeddingModel) {
        this.locations = locations;
        this.embeddings = embeddings;
        this.embeddingModel = embeddingModel;
    }

    public EmbedSummary embedMissing(String model) {
        return embedMissing(model, DEFAULT_BATCH_SIZE);
    }

    public EmbedSummary embedMissing(String model, int batchSize) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("An embedding model name is required");
        }

        Set<String> alreadyEmbedded = new HashSet<>();

        for (PlaceEmbedding embedding : embeddings.findAll()) {
            alreadyEmbedded.add(embedding.getLocationId());
        }

        List<Location> pending = locations.findAll().stream()
            .filter(location -> location.getId() != null)
            .filter(location -> location.getName() != null && !location.getName().isBlank())
            .filter(location -> !alreadyEmbedded.contains(location.getId()))
            .toList();

        int embedded = 0;
        int failedBatches = 0;
        int size = Math.max(1, batchSize);

        for (int start = 0; start < pending.size(); start += size) {
            List<Location> batch = pending.subList(start, Math.min(start + size, pending.size()));
            List<String> texts = batch.stream().map(PlaceDocumentBuilder::build).toList();

            try {
                List<float[]> vectors = embeddingModel.embed(texts);

                if (vectors == null || vectors.size() != batch.size()) {
                    failedBatches++;
                    continue;
                }

                List<PlaceEmbedding> rows = new ArrayList<>(batch.size());

                for (int i = 0; i < batch.size(); i++) {
                    float[] vector = vectors.get(i);

                    if (vector != null && vector.length > 0) {
                        rows.add(new PlaceEmbedding(batch.get(i).getId(), model, vector));
                    }
                }

                embeddings.saveAll(rows);
                embedded += rows.size();
            } catch (RuntimeException ex) {
                failedBatches++;
                log.warn("Embedding batch {}..{} failed: {}", start, start + batch.size(),
                    ex.getMessage());
            }
        }

        EmbedSummary summary = new EmbedSummary(pending.size(), embedded, failedBatches);
        log.info("Place embedding: {}", summary.describe());

        return summary;
    }

    /** Counts from one run. */
    public record EmbedSummary(int candidates, int embedded, int failedBatches) {

        public String describe() {
            return "candidates=" + candidates
                + " embedded=" + embedded
                + " failedBatches=" + failedBatches;
        }
    }
}
