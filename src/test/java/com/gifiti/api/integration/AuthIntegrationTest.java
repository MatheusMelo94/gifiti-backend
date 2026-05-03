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
            registerTestUser("duplicate@example.com", "BlueP4nther$Xyz2!");

            // Second registration with same email
            RegisterRequest request = RegisterRequest.builder()
                    .email("duplicate@example.com")
                    .password("BlueP4nther$Diff2!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    // Message changed from "Resource already exists" (generic) to
                    // "Email already registered" (specific) when the auth path
                    // moved to its own keyed exception (error.email.already.registered).
                    .andExpect(jsonPath("$.message").value(containsString("already registered")));
        }

        @Test
        @DisplayName("should reject invalid email format")
        void shouldRejectInvalidEmail() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("not-an-email")
                    .password("BlueP4nther$Xyz2!")
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
            registerTestUser("login@example.com", "BlueP4nther$Xyz2!");

            // Login
            LoginRequest request = LoginRequest.builder()
                    .email("login@example.com")
                    .password("BlueP4nther$Xyz2!")
                    .build();

            // Cookie-based auth: tokens are @JsonIgnore'd in AuthResponse
            // and delivered as HttpOnly cookies. JSON exposes user info +
            // expiresIn only. The Set-Cookie header must carry both tokens.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.email").value("login@example.com"))
                    .andExpect(jsonPath("$.expiresIn").isNumber())
                    .andExpect(cookie().exists("access_token"))
                    .andExpect(cookie().httpOnly("access_token", true))
                    .andExpect(cookie().exists("refresh_token"));
        }

        @Test
        @DisplayName("should reject invalid password")
        void shouldRejectInvalidPassword() throws Exception {
            // Register first
            registerTestUser("wrongpass@example.com", "BlueP4nther$Xyz2!");

            // Login with wrong password
            LoginRequest request = LoginRequest.builder()
                    .email("wrongpass@example.com")
                    .password("BlueP4nther$Wrong2!")
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
                    .password("BlueP4nther$Xyz2!")
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
                    .password("BlueP4nther$Xyz2!")
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
                    .password("BlueP4nther$Xyz2!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Correlation-ID", correlationId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(header().string("X-Correlation-ID", correlationId));
        }
    }
}
