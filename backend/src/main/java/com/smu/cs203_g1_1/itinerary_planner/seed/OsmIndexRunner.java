package com.smu.cs203_g1_1.itinerary_planner.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Imports the full Singapore POI index on startup when
 * {@code app.index.enabled=true}.
 *
 * <p>Unlike the curated seed, a missing or unreadable index is only logged: the
 * index is an optional enrichment, and the application must still boot and
 * serve the 49 curated places without it.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.index", name = "enabled", havingValue = "true")
public class OsmIndexRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OsmIndexRunner.class);

    private final OsmIndexImporter importer;
    private final Path path;

    public OsmIndexRunner(OsmIndexImporter importer,
                          @Value("${app.index.path:../data/osm/sg-pois.geojson}") String path) {
        this.importer = importer;
        this.path = Path.of(path);
    }

    @Override
    public void run(String... args) {
        log.info("OSM index import requested (path={}, readable={})",
            path.toAbsolutePath(), Files.isReadable(path));

        if (!Files.isReadable(path)) {
            log.warn("OSM index not found at {}; run scripts/build-sg-pois.sh to generate it", path);
            return;
        }

        try {
            importer.importFrom(path);
        } catch (RuntimeException ex) {
            log.error("OSM index import failed ({}); continuing with curated places only",
                ex.getMessage());
        }
    }
}
