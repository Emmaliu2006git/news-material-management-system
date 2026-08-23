package com.example.newsmaterial.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageConfigurationTest {
    private String priorAccessKey;
    private String priorSecretKey;

    @BeforeEach
    void provideTestCredentials() {
        priorAccessKey = System.getProperty("aws.accessKeyId");
        priorSecretKey = System.getProperty("aws.secretAccessKey");
        System.setProperty("aws.accessKeyId", "test-access-key");
        System.setProperty("aws.secretAccessKey", "test-secret-key");
    }

    @AfterEach
    void restoreCredentials() {
        restore("aws.accessKeyId", priorAccessKey);
        restore("aws.secretAccessKey", priorSecretKey);
    }

    @Test
    void minioEndpointAndPathStyleAreAppliedToPresignedUrls() {
        new ApplicationContextRunner()
                .withUserConfiguration(S3StorageConfiguration.class)
                .withPropertyValues(
                        "app.storage.region=us-east-1",
                        "app.storage.bucket=news-material-private",
                        "app.storage.endpoint=http://minio:9000",
                        "app.storage.path-style=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    S3StorageAdapter adapter = new S3StorageAdapter(
                            context.getBean(software.amazon.awssdk.services.s3.S3Client.class),
                            context.getBean(S3Presigner.class),
                            "news-material-private");

                    String url = adapter.createUploadTarget(UUID.randomUUID(), "uploads/file.jpg",
                            "image/jpeg", 12, "0".repeat(64), Instant.now().plusSeconds(300)).uploadUrl();

                    assertThat(url).startsWith("http://minio:9000/news-material-private/uploads/file.jpg?");
                });
    }

    private void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
