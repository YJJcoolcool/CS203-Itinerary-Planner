package com.smu.cs203_g1_1.itinerary_planner.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the common Flyway migrations on H2 and validates the resulting schema
 * against the JPA entities. V3 is read from a test-only copy with one statement
 * per ALTER action because H2 rejects Postgres's comma-separated form. V10 uses
 * a partial index and lives outside the common folder, so it is not reachable
 * from the test locations and is intentionally excluded.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration-h2",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:flywaytest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "app.polling.enabled=false"})
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pollingMigrationsRunAndMatchTheEntities() {
        assertTrue(columnExists("SUGGESTIONS", "TRIGGER_SOURCE"),
                "V8 should add suggestions.trigger_source");
        assertFalse(columnExists("SUGGESTIONS", "NOTIFIED_AT"),
                "V9 should drop suggestions.notified_at");
        assertFalse(indexExists("IDX_SUGGESTIONS_ACTIVITY_STATUS"),
                "V9 should drop the superseded activity/status index");
        assertTrue(indexExists("IDX_SUGGESTIONS_ACTIVITY_STATUS_RESPONDED"),
                "V8 should keep the wider activity/status/responded index");
    }

    @Test
    void countryCodeMigrationsRunAndMatchTheEntities() {
        assertTrue(columnExists("TRIPS", "COUNTRY_CODE"),
                "V11 should add trips.country_code");
        assertTrue(indexExists("IDX_TRIPS_COUNTRY_CODE"),
                "V11 should index trips.country_code");
        assertTrue(columnExists("LOCATION", "COUNTRY_CODE"),
                "V12 should add location.country_code");
        assertTrue(indexExists("IDX_LOCATION_COUNTRY_CODE"),
                "V12 should index location.country_code");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE UPPER(index_name) = UPPER(?)",
                Integer.class, indexName);
        return count != null && count > 0;
    }
}
