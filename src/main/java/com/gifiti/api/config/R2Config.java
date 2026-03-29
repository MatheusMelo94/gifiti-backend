package com.gifiti.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configures the S3 client to connect to Cloudflare R2.
 * R2 is S3-compatible, so we use the standard AWS SDK with a custom endpoint.
 */
@Configuration
public class R2Config {

    @Bean
    public S3Client s3Client(
            @Value("${app.r2.access-key-id}") String accessKeyId,
            @Value("${app.r2.secret-access-key}") String secretAccessKey,
            @Value("${app.r2.endpoint}") String endpoint) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .region(Region.of("auto"))
                .forcePathStyle(true)
                .build();
    }
}
