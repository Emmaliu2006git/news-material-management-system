package com.example.newsmaterial.material;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentVersionSchemaContractTest {
    @Test
    void forwardMigrationConstrainsCurrentVersionToItsOwningMaterialAndDefersTheCycle() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V5__enforce_material_current_version_ownership.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .toLowerCase();

        assertThat(sql)
                .contains("unique (material_id, id)")
                .contains("foreign key (id, current_version_id)")
                .contains("references material_versions (material_id, id)")
                .contains("deferrable initially deferred");
    }
}
