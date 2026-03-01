package com.gifiti.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web configuration for CORS and other web-related settings.
 *
 * Security hardening (H-01 fix):
 * - Uses allowedOriginPatterns for compatibility with allowCredentials
 * - Validates origins at startup to prevent misconfiguration
 * - Restricts to explicitly configured origins only (no wildcards)
 * - Allowed headers explicitly listed
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // Security: Validate no wildcards are used with credentials
        for (String origin : origins) {
            if (origin.equals("*")) {
                throw new IllegalStateException(
                    "CORS configuration error: wildcard (*) cannot be used with allowCredentials(true). " +
                    "Specify explicit origins in CORS_ALLOWED_ORIGINS."
                );
            }
        }

        log.info("CORS configured for origins: {}", Arrays.toString(origins));

        // Use allowedOriginPatterns which properly supports allowCredentials with multiple origins
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Correlation-ID")
                .exposedHeaders("X-Correlation-ID")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
