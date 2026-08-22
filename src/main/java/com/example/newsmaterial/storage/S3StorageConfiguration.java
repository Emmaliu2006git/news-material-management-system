package com.example.newsmaterial.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration @Profile("!local")
class S3StorageConfiguration {
    @Bean S3Client s3Client(@Value("${app.storage.region:us-east-1}") String region){return S3Client.builder().region(Region.of(region)).build();}
    @Bean S3Presigner s3Presigner(@Value("${app.storage.region:us-east-1}") String region){return S3Presigner.builder().region(Region.of(region)).build();}
}
