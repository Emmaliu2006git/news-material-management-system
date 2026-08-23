package com.example.newsmaterial.deployment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DevWorkflowContractTest {
    @Test
    void localDevelopmentEnablesScheduledImageAnalysis() throws Exception {
        Path devScript = Path.of("scripts/dev.sh");
        assertThat(devScript).isRegularFile();

        assertThat(Files.readString(devScript))
                .contains("--app.analysis.scheduling-enabled=true");
    }
}
