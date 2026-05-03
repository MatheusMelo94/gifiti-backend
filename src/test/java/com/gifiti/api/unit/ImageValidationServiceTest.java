package com.gifiti.api.unit;

import com.gifiti.api.exception.ImageUploadException;
import com.gifiti.api.service.ImageValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidationServiceTest {

    private ImageValidationService validationService;

    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    // WebP magic bytes: RIFF....WEBP
    private static final byte[] WEBP_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    // PDF magic bytes: %PDF
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46};
    // EXE magic bytes: MZ
    private static final byte[] EXE_MAGIC = new byte[]{0x4D, 0x5A};

    @BeforeEach
    void setUp() {
        validationService = new ImageValidationService(5_242_880L); // 5MB
    }

    @Nested
    @DisplayName("Valid files")
    class ValidFiles {

        @Test
        @DisplayName("should accept valid JPEG file")
        void shouldAcceptValidJpeg() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", JPEG_MAGIC);
            assertThatNoException().isThrownBy(() -> validationService.validate(file));
        }

        @Test
        @DisplayName("should accept valid PNG file")
        void shouldAcceptValidPng() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.png", "image/png", PNG_MAGIC);
            assertThatNoException().isThrownBy(() -> validationService.validate(file));
        }

        @Test
        @DisplayName("should accept valid WebP file")
        void shouldAcceptValidWebP() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.webp", "image/webp", WEBP_MAGIC);
            assertThatNoException().isThrownBy(() -> validationService.validate(file));
        }

        @Test
        @DisplayName("should accept .jpeg extension")
        void shouldAcceptJpegExtension() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpeg", "image/jpeg", JPEG_MAGIC);
            assertThatNoException().isThrownBy(() -> validationService.validate(file));
        }
    }

    @Nested
    @DisplayName("Invalid files")
    class InvalidFiles {

        @Test
        @DisplayName("should reject null file")
        void shouldRejectNullFile() {
            assertThatThrownBy(() -> validationService.validate(null))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.required");
        }

        @Test
        @DisplayName("should reject empty file")
        void shouldRejectEmptyFile() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.empty");
        }

        @Test
        @DisplayName("should reject file exceeding max size")
        void shouldRejectOversizedFile() {
            byte[] oversized = new byte[5_242_881]; // 5MB + 1 byte
            System.arraycopy(JPEG_MAGIC, 0, oversized, 0, JPEG_MAGIC.length);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "huge.jpg", "image/jpeg", oversized);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.too.large");
        }

        @Test
        @DisplayName("should reject invalid extension (.pdf)")
        void shouldRejectPdfExtension() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "document.pdf", "application/pdf", PDF_MAGIC);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.type.not.allowed");
        }

        @Test
        @DisplayName("should reject invalid extension (.exe)")
        void shouldRejectExeExtension() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "malware.exe", "application/octet-stream", EXE_MAGIC);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.type.not.allowed");
        }

        @Test
        @DisplayName("should reject invalid extension (.gif)")
        void shouldRejectGifExtension() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "animation.gif", "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38});
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.type.not.allowed");
        }

        @Test
        @DisplayName("should reject invalid MIME type")
        void shouldRejectInvalidMimeType() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "application/pdf", JPEG_MAGIC);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.type.not.allowed");
        }

        @Test
        @DisplayName("should reject spoofed file (jpg extension but PDF content)")
        void shouldRejectSpoofedJpgWithPdfContent() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.jpg", "image/jpeg", PDF_MAGIC);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.content.mismatch");
        }

        @Test
        @DisplayName("should reject spoofed file (jpg extension but EXE content)")
        void shouldRejectSpoofedJpgWithExeContent() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.jpg", "image/jpeg", EXE_MAGIC);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.content.mismatch");
        }

        @Test
        @DisplayName("should reject file with no extension")
        void shouldRejectNoExtension() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "noextension", "image/jpeg", JPEG_MAGIC);
            assertThatThrownBy(() -> validationService.validate(file))
                    .isInstanceOf(ImageUploadException.class)
                    .hasMessage("error.image.validation.type.not.allowed");
        }
    }
}
