package com.gifiti.api.integration;

import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication endpoints.
 */
class AuthIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("should register a new user successfully")
        void shouldRegisterNewUser() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("newuser@example.com")
                    .password("SecurePass123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.email").value("newuser@example.com"))
                    .andExpect(jsonPath("$.message").value(
                            "Registration successful. Please check your email to verify your account."));
        }

        @Test
        @DisplayName("should reject duplicate email")
        void shouldRejectDuplicateEmail() throws Exception {
            // First registration
            registerTestUser("duplicate@example.com", "Password123!");

            // Second registration with same email
            RegisterRequest request = RegisterRequest.builder()
                    .email("duplicate@example.com")
                    .password("DifferentPass123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("should reject invalid email format")
        void shouldRejectInvalidEmail() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("not-an-email")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject short password")
        void shouldRejectShortPassword() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("user@example.com")
                    .password("short")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("should login successfully with valid credentials")
        void shouldLoginSuccessfully() throws Exception {
            // Register first
            registerTestUser("login@example.com", "Password123!");

            // Login
            LoginRequest request = LoginRequest.builder()
                    .email("login@example.com")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.expiresIn").isNumber());
        }

        @Test
        @DisplayName("should reject invalid password")
        void shouldRejectInvalidPassword() throws Exception {
            // Register first
            registerTestUser("wrongpass@example.com", "CorrectPassword123!");

            // Login with wrong password
            LoginRequest request = LoginRequest.builder()
                    .email("wrongpass@example.com")
                    .password("WrongPassword123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject non-existent user")
        void shouldRejectNonExistentUser() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("nonexistent@example.com")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Correlation ID")
    class CorrelationIdTests {

        @Test
        @DisplayName("should return X-Correlation-ID header in response")
        void shouldReturnCorrelationIdHeader() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("correlation@example.com")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(header().exists("X-Correlation-ID"));
        }

        @Test
        @DisplayName("should echo provided X-Correlation-ID header")
        void shouldEchoProvidedCorrelationId() throws Exception {
            String correlationId = "test-correlation-123";

            RegisterRequest request = RegisterRequest.builder()
                    .email("echo-correlation@example.com")
                    .password("Password123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Correlation-ID", correlationId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(header().string("X-Correlation-ID", correlationId));
        }
    }
}
