package com.smu.cs203_g1_1.itinerary_planner.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the offline place seed on startup when {@code app.seed.enabled=true}.
 *
 * <p>Deliberately catches nothing: a corrupt seed should fail startup loudly.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class PlaceSeedRunner implements CommandLineRunner {

    private final PlaceSeeder placeSeeder;

    public PlaceSeedRunner(PlaceSeeder placeSeeder) {
        this.placeSeeder = placeSeeder;
    }

    @Override
    public void run(String... args) {
        placeSeeder.seed();
    }
}
