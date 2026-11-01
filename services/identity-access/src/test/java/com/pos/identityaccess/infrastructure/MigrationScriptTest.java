package com.pos.identityaccess.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationScriptTest {

    @Test
    void shouldContainOrderedP0MigrationsWithColumnComments() throws IOException {
        List<String> migrations = List.of(
                "db/migration/V1__identity_schema.sql",
                "db/migration/V2__membership_schema.sql",
                "db/migration/V3__idempotency_outbox_schema.sql",
                "db/migration/V4__seed_local_bootstrap.sql");

        for (String migration : migrations) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(migration)) {
                assertThat(stream).as("migration %s", migration).isNotNull();
                String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(sql).contains("COMMENT ON COLUMN");
                assertThat(sql).doesNotContain("pin_hash", "pos_devices", "pos_pins");
            }
        }
    }
}
