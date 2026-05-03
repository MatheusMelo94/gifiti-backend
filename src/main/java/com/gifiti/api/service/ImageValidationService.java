package com.gifiti.api.service;

import com.gifiti.api.exception.ImageUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Validates uploaded image files using triple-layer security:
 * 1. File extension check
 * 2. MIME type check
 * 3. Magic bytes (file signature) check
 */
@Slf4j
@Service
public class ImageValidationService {

    private final long maxFileSize;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    // Magic bytes for supported image formats
    private static final byte[] JPEG_SIGNATURE = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] WEBP_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MARKER = new byte[]{0x57, 0x45, 0x42, 0x50};

    // Maps MIME types to their magic byte validators
    private static final Map<String, java.util.function.Predicate<byte[]>> MAGIC_VALIDATORS = Map.of(
            "image/jpeg", ImageValidationService::isJpeg,
            "image/png", ImageValidationService::isPng,
            "image/webp", ImageValidationService::isWebP
    );

    public ImageValidationService(@Value("${app.upload.max-file-size:5242880}") long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void validate(MultipartFile file) {
        if (file == null) {
            throw new ImageUploadException("error.image.validation.required", new Object[0]);
        }
        if (file.isEmpty() || file.getSize() == 0) {
            throw new ImageUploadException("error.image.validation.empty", new Object[0]);
        }
        if (file.getSize() > maxFileSize) {
            throw new ImageUploadException("error.image.validation.too.large", new Object[0]);
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("SECURITY_EVENT: Rejected file with disallowed extension: {}", extension);
            throw new ImageUploadException("error.image.validation.type.not.allowed", new Object[0]);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("SECURITY_EVENT: Rejected file with disallowed MIME type: {}", contentType);
            throw new ImageUploadException("error.image.validation.type.not.allowed", new Object[0]);
        }

        try {
            byte[] bytes = file.getBytes();
            var validator = MAGIC_VALIDATORS.get(contentType.toLowerCase());
            if (validator != null && !validator.test(bytes)) {
                log.warn("SECURITY_EVENT: File magic bytes mismatch for claimed type: {}", contentType);
                throw new ImageUploadException("error.image.validation.content.mismatch", new Object[0]);
            }
        } catch (IOException e) {
            throw new ImageUploadException("error.image.upload.read.failed", new Object[0], e);
        }
    }

    public String getExtensionForMimeType(String mimeType) {
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ImageUploadException("error.image.validation.mime.unsupported", mimeType);
        };
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && matchesSignature(bytes, JPEG_SIGNATURE);
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 4 && matchesSignature(bytes, PNG_SIGNATURE);
    }

    private static boolean isWebP(byte[] bytes) {
        return bytes.length >= 12
                && matchesSignature(bytes, WEBP_RIFF)
                && bytes[8] == WEBP_MARKER[0]
                && bytes[9] == WEBP_MARKER[1]
                && bytes[10] == WEBP_MARKER[2]
                && bytes[11] == WEBP_MARKER[3];
    }

    private static boolean matchesSignature(byte[] data, byte[] signature) {
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
