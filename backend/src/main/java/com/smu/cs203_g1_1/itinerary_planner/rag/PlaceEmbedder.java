package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.smu.cs203_g1_1.itinerary_planner.trips.Location;
import com.smu.cs203_g1_1.itinerary_planner.trips.LocationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
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

    private static final int MAX_VECTOR_BYTES = 8192;

    private final LocationRepository locations;
    private final PlaceEmbeddingRepository embeddings;
    private final EmbeddingModel embeddingModel;
    private final int dims;

    public PlaceEmbedder(LocationRepository locations,
                         PlaceEmbeddingRepository embeddings,
                         EmbeddingModel embeddingModel,
                         @Value("${spring.ai.openai.embedding.dimensions:512}") int dims) {
        this.locations = locations;
        this.embeddings = embeddings;
        this.embeddingModel = embeddingModel;
        this.dims = dims;
    }

    public EmbedSummary embedMissing(String model) {
        return embedMissing(model, DEFAULT_BATCH_SIZE);
    }

    public EmbedSummary embedMissing(String model, int batchSize) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("An embedding model name is required");
        }

        Map<String, PlaceEmbedding> alreadyEmbedded = new HashMap<>();

        for (PlaceEmbedding embedding : embeddings.findAll()) {
            if (embedding.getLocationId() != null) {
                alreadyEmbedded.put(embedding.getLocationId(), embedding);
            }
        }

        List<Location> pending = locations.findTrusted().stream()
            .filter(location -> location.getId() != null)
            .filter(location -> location.getName() != null && !location.getName().isBlank())
            .filter(location -> needsEmbedding(alreadyEmbedded.get(location.getId()), model))
            .toList();

        int embedded = 0;
        int failedBatches = 0;
        int size = Math.max(1, batchSize);

        for (int start = 0; start < pending.size(); start += size) {
            List<Location> batch = pending.subList(start, Math.min(start + size, pending.size()));
            List<String> texts = batch.stream().map(PlaceDocumentBuilder::build).toList();

            List<float[]> vectors;
            try {
                vectors = embeddingModel.embed(texts);
            } catch (RuntimeException ex) {
                failedBatches++;
                log.warn("Embedding batch {}..{} failed: {}", start, start + batch.size(),
                    ex.getMessage());
                continue;
            }

            if (vectors == null || vectors.size() != batch.size()) {
                failedBatches++;
                continue;
            }

            List<PlaceEmbedding> rows = new ArrayList<>(batch.size());

            for (int i = 0; i < batch.size(); i++) {
                float[] vector = vectors.get(i);

                if (vector != null && vector.length > 0) {
                    ensureWithinByteCap(model, vector);
                    rows.add(new PlaceEmbedding(batch.get(i).getId(), model, vector));
                }
            }

            try {
                embeddings.saveAll(rows);
                embedded += rows.size();
            } catch (RuntimeException ex) {
                failedBatches++;
                log.warn("Saving embedding batch {}..{} failed: {}",
                    start, start + batch.size(), ex.getMessage());
            }
        }

        EmbedSummary summary = new EmbedSummary(pending.size(), embedded, failedBatches);
        log.info("Place embedding: {}", summary.describe());

        return summary;
    }

    private boolean needsEmbedding(PlaceEmbedding existing, String model) {
        return existing == null
            || !model.equals(existing.getModel())
            || existing.getDims() != dims;
    }

    private static void ensureWithinByteCap(String model, float[] vector) {
        int bytes = vector.length * Float.BYTES;

        if (bytes > MAX_VECTOR_BYTES) {
            throw new IllegalStateException("Embedding model '" + model + "' returned "
                + vector.length + " dimensions (" + bytes + " bytes), exceeding the "
                + MAX_VECTOR_BYTES + "-byte place_embedding.vector cap");
        }
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
