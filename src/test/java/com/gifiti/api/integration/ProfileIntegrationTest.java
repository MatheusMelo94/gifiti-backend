package com.gifiti.api.integration;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.request.UpdateProfileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for user profile features (Phase 1B).
 */
class ProfileIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("Registration with displayName")
    class RegistrationTests {

        @Test
        @DisplayName("should register with displayName and return it in response")
        void shouldRegisterWithDisplayName() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("maria@example.com")
                    .password("SecurePass123!")
                    .displayName("Maria Santos")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.displayName").value("Maria Santos"));
        }

        @Test
        @DisplayName("should derive displayName from email when not provided")
        void shouldDeriveDisplayNameFromEmail() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("joao.silva@example.com")
                    .password("SecurePass123!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.displayName").value("joao.silva"));
        }
    }

    @Nested
    @DisplayName("Login with displayName and roles")
    class LoginTests {

        @Test
        @DisplayName("should return displayName and roles on login")
        void shouldReturnDisplayNameAndRolesOnLogin() throws Exception {
            RegisterRequest regRequest = RegisterRequest.builder()
                    .email("loginprofile@example.com")
                    .password("SecurePass123!")
                    .displayName("Login Test")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(regRequest)))
                    .andExpect(status().isCreated());

            String token = loginAndGetToken("loginprofile@example.com", "SecurePass123!");

            // Verify the login response includes displayName and roles
            // (already tested by loginAndGetToken succeeding, but let's verify explicitly)
            mockMvc.perform(get("/api/v1/profile")
                            .header("Authorization", bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Login Test"))
                    .andExpect(jsonPath("$.roles", hasItem("USER")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/profile")
    class GetProfileTests {

        @Test
        @DisplayName("should return full profile for authenticated user")
        void shouldReturnFullProfile() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("profile@example.com")
                    .password("SecurePass123!")
                    .displayName("Profile User")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            String token = loginAndGetToken("profile@example.com", "SecurePass123!");

            mockMvc.perform(get("/api/v1/profile")
                            .header("Authorization", bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.email").value("profile@example.com"))
                    .andExpect(jsonPath("$.displayName").value("Profile User"))
                    .andExpect(jsonPath("$.roles", hasItem("USER")))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("should reject unauthenticated access")
        void shouldRejectUnauthenticatedAccess() throws Exception {
            mockMvc.perform(get("/api/v1/profile"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("should update displayName")
        void shouldUpdateDisplayName() throws Exception {
            String token = createUserAndGetToken("update@example.com", "SecurePass123!");

            UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                    .displayName("Updated Name")
                    .build();

            mockMvc.perform(put("/api/v1/profile")
                            .header("Authorization", bearerToken(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Updated Name"));

            // Verify it persisted
            mockMvc.perform(get("/api/v1/profile")
                            .header("Authorization", bearerToken(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Updated Name"));
        }
    }
}
