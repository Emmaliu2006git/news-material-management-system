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
                .containsEntry("SPRING_DATASOURCE_URL", "${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/news_material}")
                .containsEntry("NEWS_MATERIAL_S3_ENDPOINT", "${NEWS_MATERIAL_S3_ENDPOINT:-http://minio:9000}")
                .containsEntry("NEWS_MATERIAL_S3_PATH_STYLE", "${NEWS_MATERIAL_S3_PATH_STYLE:-true}");
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
        assertThat(command).contains("mb", "anonymous set none", "NEWS_MATERIAL_S3_BUCKET");

        Map<String, Object> volumes = map(document.get("volumes"));
        assertThat(volumes).containsKeys("postgres-data", "minio-data");
    }

    @Test
    void composeMapsEveryDocumentedOverrideAndExampleInput() throws Exception {
        Map<String, Object> document = new Yaml().load(Files.readString(Path.of("docker-compose.yml")));
        Map<String, Object> services = map(document.get("services"));
        Map<String, Object> app = map(map(services.get("app")).get("environment"));

        assertThat(app)
                .containsEntry("SERVER_PORT", "${SERVER_PORT:-8080}")
                .containsEntry("SERVER_SERVLET_SESSION_COOKIE_SECURE", "${SERVER_SERVLET_SESSION_COOKIE_SECURE:-false}")
                .containsEntry("SPRING_DATASOURCE_URL", "${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/news_material}")
                .containsEntry("SPRING_DATASOURCE_USERNAME", "${SPRING_DATASOURCE_USERNAME:-news_material}")
                .containsEntry("SPRING_DATASOURCE_PASSWORD", "${POSTGRES_PASSWORD:-demo-only-postgres}")
                .containsEntry("AWS_ACCESS_KEY_ID", "${MINIO_ROOT_USER:-demo-minio-user}")
                .containsEntry("AWS_SECRET_ACCESS_KEY", "${MINIO_ROOT_PASSWORD:-demo-only-minio-password}")
                .containsEntry("NEWS_MATERIAL_S3_BUCKET", "${NEWS_MATERIAL_S3_BUCKET:-news-material-private}")
                .containsEntry("AWS_REGION", "${AWS_REGION:-us-east-1}")
                .containsEntry("NEWS_MATERIAL_S3_ENDPOINT", "${NEWS_MATERIAL_S3_ENDPOINT:-http://minio:9000}")
                .containsEntry("NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT", "${NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT:-http://localhost:9000}")
                .containsEntry("NEWS_MATERIAL_S3_PATH_STYLE", "${NEWS_MATERIAL_S3_PATH_STYLE:-true}")
                .containsEntry("NEWS_MATERIAL_AI_PROVIDER", "${NEWS_MATERIAL_AI_PROVIDER:-local}")
                .containsEntry("OPENAI_API_KEY", "${OPENAI_API_KEY:-}")
                .containsEntry("OPENAI_BASE_URL", "${OPENAI_BASE_URL:-https://api.openai.com/v1}")
                .containsEntry("OPENAI_VISION_MODEL", "${OPENAI_VISION_MODEL:-gpt-5-mini}")
                .containsEntry("OPENAI_CONNECT_TIMEOUT", "${OPENAI_CONNECT_TIMEOUT:-PT3S}")
                .containsEntry("OPENAI_REQUEST_TIMEOUT", "${OPENAI_REQUEST_TIMEOUT:-PT20S}")
                .containsEntry("ANALYSIS_LEASE_DURATION", "${ANALYSIS_LEASE_DURATION:-PT5M}")
                .containsEntry("APP_ANALYSIS_SCHEDULING_ENABLED", "${APP_ANALYSIS_SCHEDULING_ENABLED:-true}")
                .containsEntry("APP_ANALYSIS_MAX_ATTEMPTS", "${APP_ANALYSIS_MAX_ATTEMPTS:-3}")
                .containsEntry("APP_ANALYSIS_BASE_BACKOFF", "${APP_ANALYSIS_BASE_BACKOFF:-PT1M}")
                .containsEntry("APP_ANALYSIS_POLL_DELAY_MS", "${APP_ANALYSIS_POLL_DELAY_MS:-5000}")
                .containsEntry("APP_UPLOAD_MAX_FILES", "${APP_UPLOAD_MAX_FILES:-10}")
                .containsEntry("APP_UPLOAD_MAX_FILE_BYTES", "${APP_UPLOAD_MAX_FILE_BYTES:-52428800}")
                .containsEntry("APP_UPLOAD_MAX_TOTAL_BYTES", "${APP_UPLOAD_MAX_TOTAL_BYTES:-209715200}")
                .containsEntry("APP_UPLOAD_INTENT_TTL", "${APP_UPLOAD_INTENT_TTL:-PT15M}");

        Map<String, Object> appService = map(services.get("app"));
        assertThat(appService.get("ports").toString()).contains("${SERVER_PORT:-8080}:${SERVER_PORT:-8080}");
        assertThat(map(appService.get("healthcheck")).get("test").toString())
                .contains("http://localhost:${SERVER_PORT:-8080}/actuator/health");

        assertThat(map(map(services.get("postgres")).get("environment")))
                .containsEntry("POSTGRES_PASSWORD", "${POSTGRES_PASSWORD:-demo-only-postgres}");
        assertThat(map(map(services.get("minio")).get("environment")))
                .containsEntry("MINIO_ROOT_USER", "${MINIO_ROOT_USER:-demo-minio-user}")
                .containsEntry("MINIO_ROOT_PASSWORD", "${MINIO_ROOT_PASSWORD:-demo-only-minio-password}")
                .containsEntry("MINIO_API_CORS_ALLOW_ORIGIN", "${MINIO_API_CORS_ALLOW_ORIGIN:-http://localhost:8080}");
        assertThat(map(map(services.get("minio-init")).get("environment")))
                .containsEntry("MINIO_ROOT_USER", "${MINIO_ROOT_USER:-demo-minio-user}")
                .containsEntry("MINIO_ROOT_PASSWORD", "${MINIO_ROOT_PASSWORD:-demo-only-minio-password}")
                .containsEntry("NEWS_MATERIAL_S3_BUCKET", "${NEWS_MATERIAL_S3_BUCKET:-news-material-private}");
        assertThat(exampleKeys()).containsExactlyInAnyOrder(
                "POSTGRES_PASSWORD", "MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD",
                "MINIO_API_CORS_ALLOW_ORIGIN", "SERVER_PORT",
                "SERVER_SERVLET_SESSION_COOKIE_SECURE", "SPRING_DATASOURCE_URL",
                "SPRING_DATASOURCE_USERNAME", "NEWS_MATERIAL_S3_BUCKET", "AWS_REGION",
                "NEWS_MATERIAL_S3_ENDPOINT", "NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT",
                "NEWS_MATERIAL_S3_PATH_STYLE", "NEWS_MATERIAL_AI_PROVIDER", "OPENAI_API_KEY",
                "OPENAI_BASE_URL", "OPENAI_VISION_MODEL", "OPENAI_CONNECT_TIMEOUT",
                "OPENAI_REQUEST_TIMEOUT", "ANALYSIS_LEASE_DURATION",
                "APP_ANALYSIS_SCHEDULING_ENABLED", "APP_ANALYSIS_MAX_ATTEMPTS",
                "APP_ANALYSIS_BASE_BACKOFF", "APP_ANALYSIS_POLL_DELAY_MS",
                "APP_UPLOAD_MAX_FILES", "APP_UPLOAD_MAX_FILE_BYTES",
                "APP_UPLOAD_MAX_TOTAL_BYTES", "APP_UPLOAD_INTENT_TTL");
    }

    private static java.util.Set<String> exampleKeys() throws Exception {
        return Files.readAllLines(Path.of(".env.example")).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                .map(line -> line.substring(0, line.indexOf('=')))
                .collect(java.util.stream.Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
