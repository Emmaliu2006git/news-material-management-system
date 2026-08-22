package com.example.newsmaterial.material;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VersionContentSchemaContractTest {
    @Test
    void migrationRequiresTextBodyForTextButLeavesFileRowsExtensible() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("db/migration/V2__materials_and_versions.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("content_kind");
            assertThat(sql).contains("content_kind = 'TEXT'");
            assertThat(sql).contains("btrim(text_body) <> ''");
            assertThat(sql).contains("content_kind = 'FILE'");
        }
    }
}
