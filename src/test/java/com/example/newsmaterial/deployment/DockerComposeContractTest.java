package com.example.newsmaterial.deployment;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DockerComposeContractTest {
    @Test
    void demoStackHasPrivateDurableStorageAndReadinessGates() throws Exception {
        Path composeFile = Path.of("docker-compose.yml");
        assertThat(composeFile).isRegularFile();

        Map<String, Object> document = new Yaml().load(Files.readString(composeFile));
        Map<String, Object> services = map(document.get("services"));
        assertThat(services).containsKeys("app", "postgres", "minio", "minio-init");

        Map<String, Object> app = map(services.get("app"));
        Map<String, Object> appEnvironment = map(app.get("environment"));
        assertThat(appEnvironment)
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/news_material")
                .containsEntry("NEWS_MATERIAL_S3_ENDPOINT", "http://minio:9000")
                .containsEntry("NEWS_MATERIAL_S3_PATH_STYLE", "true");
        assertThat(appEnvironment.get("NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT").toString())
                .contains("http://localhost:9000");
        Map<String, Object> dependencies = map(app.get("depends_on"));
        assertThat(map(dependencies.get("postgres"))).containsEntry("condition", "service_healthy");
        assertThat(map(dependencies.get("minio-init"))).containsEntry("condition", "service_completed_successfully");
        assertThat(app).containsKey("healthcheck");

        Map<String, Object> postgres = map(services.get("postgres"));
        assertThat(postgres).containsKey("healthcheck");
        assertThat(postgres.get("volumes").toString()).contains("postgres-data");

        Map<String, Object> minio = map(services.get("minio"));
        assertThat(minio).containsKey("healthcheck");
        assertThat(minio.get("volumes").toString()).contains("minio-data");

        Map<String, Object> bucketInit = map(services.get("minio-init"));
        String command = bucketInit.get("entrypoint").toString();
        assertThat(command).contains("mb", "anonymous set none", "news-material-private");

        Map<String, Object> volumes = map(document.get("volumes"));
        assertThat(volumes).containsKeys("postgres-data", "minio-data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
