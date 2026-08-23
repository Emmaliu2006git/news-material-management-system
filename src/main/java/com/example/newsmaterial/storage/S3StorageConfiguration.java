package com.example.newsmaterial.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration @Profile("!local")
class S3StorageConfiguration {
    @Bean
    S3Client s3Client(@Value("${app.storage.region:us-east-1}") String region,
                      @Value("${app.storage.endpoint:}") String endpoint,
                      @Value("${app.storage.path-style:false}") boolean pathStyle) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .forcePathStyle(pathStyle);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(@Value("${app.storage.region:us-east-1}") String region,
                            @Value("${app.storage.endpoint:}") String endpoint,
                            @Value("${app.storage.presigner-endpoint:}") String presignerEndpoint,
                            @Value("${app.storage.path-style:false}") boolean pathStyle) {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        String publicEndpoint = presignerEndpoint.isBlank() ? endpoint : presignerEndpoint;
        if (!publicEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(publicEndpoint));
        }
        return builder.build();
    }
}
