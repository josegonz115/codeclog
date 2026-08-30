package com.codeclog.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeclog.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the migration path actually runs against real Postgres, rather than that the context can be
 * assembled. A green Flyway history here is the precondition for every later milestone's schema.
 */
class SchemaBaselineIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway applied the baseline migration successfully")
    void flywayAppliedBaseline() {
        var rows = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
        assertThat(rows).first().satisfies(row -> {
            assertThat(row.get("version")).isEqualTo("1");
            assertThat(row.get("description")).isEqualTo("baseline");
        });
    }

    @Test
    @DisplayName("citext is available for case-insensitive handles")
    void citextExtensionInstalled() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'citext'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("set_updated_at() exists and stamps the column")
    void updatedAtTriggerFunctionWorks() {
        jdbc.execute(
                """
                CREATE TEMPORARY TABLE trigger_probe (
                    id bigserial PRIMARY KEY,
                    value text,
                    updated_at timestamptz NOT NULL DEFAULT now()
                )
                """);
        jdbc.execute(
                """
                CREATE TRIGGER trigger_probe_set_updated_at
                BEFORE UPDATE ON trigger_probe
                FOR EACH ROW EXECUTE FUNCTION set_updated_at()
                """);
        jdbc.update("INSERT INTO trigger_probe (value, updated_at) VALUES ('before', now() - interval '1 day')");

        jdbc.update("UPDATE trigger_probe SET value = 'after'");

        Boolean stamped = jdbc.queryForObject(
                "SELECT updated_at > now() - interval '1 minute' FROM trigger_probe", Boolean.class);
        assertThat(stamped).isTrue();
    }
}
