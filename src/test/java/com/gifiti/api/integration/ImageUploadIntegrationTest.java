package com.gifiti.api.integration;

import com.gifiti.api.service.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for POST /api/v1/uploads/image.
 * R2StorageService is mocked — we're testing validation and controller logic, not Cloudflare.
 */
class ImageUploadIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    private R2StorageService r2StorageService;

    private String userToken;

    // Magic bytes for test files
    private static final byte[] JPEG_CONTENT = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG_CONTENT = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP_CONTENT = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    private static final byte[] PDF_CONTENT = new byte[]{0x25, 0x50, 0x44, 0x46};

    @BeforeEach
    void setup() throws Exception {
        userToken = createUserAndGetToken("uploaduser@example.com", "Str0ng!Xyz#9");
    }

    @Nested
    @DisplayName("POST /api/v1/uploads/image — Success")
    class SuccessTests {

        @Test
        @DisplayName("should upload valid JPEG and return URL")
        void shouldUploadJpeg() throws Exception {
            when(r2StorageService.upload(any(byte[].class), anyString(), eq("image/jpeg")))
                    .thenReturn("https://test.r2.dev/users/u1/items/uuid.jpg");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", JPEG_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "item")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.url").value("https://test.r2.dev/users/u1/items/uuid.jpg"));
        }

        @Test
        @DisplayName("should upload valid PNG and return URL")
        void shouldUploadPng() throws Exception {
            when(r2StorageService.upload(any(byte[].class), anyString(), eq("image/png")))
                    .thenReturn("https://test.r2.dev/users/u1/items/uuid.png");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.png", "image/png", PNG_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "item")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.url").exists());
        }

        @Test
        @DisplayName("should upload valid WebP and return URL")
        void shouldUploadWebP() throws Exception {
            when(r2StorageService.upload(any(byte[].class), anyString(), eq("image/webp")))
                    .thenReturn("https://test.r2.dev/users/u1/wishlists/uuid.webp");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "cover.webp", "image/webp", WEBP_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "wishlist")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.url").exists());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/uploads/image — Authentication")
    class AuthTests {

        @Test
        @DisplayName("should reject unauthenticated request")
        void shouldRejectUnauthenticated() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", JPEG_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "item"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/uploads/image — Validation Errors")
    class ValidationTests {

        @Test
        @DisplayName("should reject invalid file type (PDF)")
        void shouldRejectPdf() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "document.pdf", "application/pdf", PDF_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "item")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject spoofed file (jpg extension but PDF content)")
        void shouldRejectSpoofedFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.jpg", "image/jpeg", PDF_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "item")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject empty file")
        void shouldRejectEmptyFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "item")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject invalid context")
        void shouldRejectInvalidContext() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", JPEG_CONTENT);

            mockMvc.perform(multipart("/api/v1/uploads/image")
                            .file(file)
                            .param("context", "profile")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isBadRequest());
        }
    }
}
