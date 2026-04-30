package com.gifiti.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests using a Testcontainers MongoDB instance
 * shared across the entire test suite via the Singleton Container pattern.
 *
 * Why not @Testcontainers + @Container: those scope the container to the
 * declaring class and stop() it on teardown. Combined with Spring's context
 * cache (which reuses the same @SpringBootTest context across classes), the
 * second class to load gets a cached MongoTemplate pointed at a stopped
 * container — every query then waits 30s for connection-refused. The
 * singleton-container pattern starts the container once per JVM and lets
 * Testcontainers' Ryuk sidecar clean up at JVM exit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static {
        mongoDBContainer.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeEach
    void cleanDatabase() {
        // Clean all collections before each test
        mongoTemplate.getCollectionNames().forEach(name -> {
            if (!name.startsWith("system.")) {
                mongoTemplate.dropCollection(name);
            }
        });
    }

    /**
     * Register a test user and return the email.
     */
    protected String registerTestUser(String email, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        return email;
    }

    /**
     * Login and return the access token (extracted from the access_token cookie).
     */
    protected String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // The JWT is set as an HttpOnly cookie; extract it from the Set-Cookie header
        jakarta.servlet.http.Cookie accessTokenCookie = result.getResponse().getCookie("access_token");
        if (accessTokenCookie != null) {
            return accessTokenCookie.getValue();
        }

        // Fallback: parse from AuthResponse JSON (for backward-compatibility)
        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
        return response.getAccessToken();
    }

    /**
     * Create a test user and return their access token.
     */
    protected String createUserAndGetToken(String email, String password) throws Exception {
        registerTestUser(email, password);
        return loginAndGetToken(email, password);
    }

    /**
     * Create authorization header value.
     */
    protected String bearerToken(String token) {
        return "Bearer " + token;
    }
}
