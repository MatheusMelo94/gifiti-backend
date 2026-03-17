package com.gifiti.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

/**
 * Web configuration for CORS and other web-related settings.
 *
 * Security hardening (H-01 fix):
 * - Uses allowedOrigins with exact string matching (no regex patterns)
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

        // Security: Validate origins are well-formed URLs (no wildcards, no regex)
        for (String origin : origins) {
            if (origin.equals("*")) {
                throw new IllegalStateException(
                    "CORS configuration error: wildcard (*) cannot be used with allowCredentials(true). " +
                    "Specify explicit origins in CORS_ALLOWED_ORIGINS."
                );
            }
            try {
                new URL(origin);
            } catch (MalformedURLException e) {
                throw new IllegalStateException(
                    "CORS configuration error: '" + origin + "' is not a valid origin URL. " +
                    "Each origin must be a full URL (e.g. https://example.com)."
                );
            }
        }

        log.info("CORS configured for origins: {}", Arrays.toString(origins));

        // Use allowedOrigins with exact string matching (not patterns) to prevent regex bypass
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Correlation-ID")
                .exposedHeaders("X-Correlation-ID")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
