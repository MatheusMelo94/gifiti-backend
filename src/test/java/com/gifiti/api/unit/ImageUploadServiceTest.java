package com.gifiti.api.unit;

import com.gifiti.api.exception.ImageUploadException;
import com.gifiti.api.service.ImageUploadService;
import com.gifiti.api.service.ImageValidationService;
import com.gifiti.api.service.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    @Mock
    private ImageValidationService validationService;

    @Mock
    private R2StorageService storageService;

    private ImageUploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new ImageUploadService(validationService, storageService);
    }

    @Test
    @DisplayName("should upload valid file and return URL")
    void shouldUploadValidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(validationService.getExtensionForMimeType("image/jpeg")).thenReturn("jpg");
        when(storageService.upload(any(byte[].class), anyString(), eq("image/jpeg")))
                .thenReturn("https://test.r2.dev/users/user1/items/uuid.jpg");

        String url = uploadService.upload(file, "item", "user1");

        assertThat(url).isEqualTo("https://test.r2.dev/users/user1/items/uuid.jpg");
        verify(validationService).validate(file);
        verify(storageService).upload(any(byte[].class), anyString(), eq("image/jpeg"));
    }

    @Test
    @DisplayName("should reject invalid context")
    void shouldRejectInvalidContext() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1});

        // Task 10: getMessage() now returns the i18n key (keyed-constructor contract,
        // per LocalizedRuntimeException javadoc); the user-visible English text is
        // resolved by GlobalExceptionHandler at response time.
        assertThatThrownBy(() -> uploadService.upload(file, "profile", "user1"))
                .isInstanceOf(ImageUploadException.class)
                .hasMessage("error.image.upload.context.invalid");
    }

    @Test
    @DisplayName("should reject null context")
    void shouldRejectNullContext() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> uploadService.upload(file, null, "user1"))
                .isInstanceOf(ImageUploadException.class)
                .hasMessage("error.image.upload.context.invalid");
    }

    @Test
    @DisplayName("should propagate validation failure")
    void shouldPropagateValidationFailure() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[]{1});

        doThrow(new ImageUploadException("File type not allowed. Accepted: JPEG, PNG, WebP"))
                .when(validationService).validate(file);

        assertThatThrownBy(() -> uploadService.upload(file, "item", "user1"))
                .isInstanceOf(ImageUploadException.class)
                .hasMessage("File type not allowed. Accepted: JPEG, PNG, WebP");
    }

    @Test
    @DisplayName("should use correct context in storage key")
    void shouldUseCorrectContextInKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", new byte[]{1, 2});

        when(validationService.getExtensionForMimeType("image/png")).thenReturn("png");
        when(storageService.upload(any(byte[].class), contains("wishlists"), eq("image/png")))
                .thenReturn("https://test.r2.dev/users/user1/wishlists/uuid.png");

        String url = uploadService.upload(file, "wishlist", "user1");

        assertThat(url).contains("wishlists");
        verify(storageService).upload(any(byte[].class), contains("wishlists"), eq("image/png"));
    }
}
