package com.gifiti.api.service;

import com.gifiti.api.exception.ImageUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates image upload: validates the file, generates a unique key, and stores in R2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final ImageValidationService validationService;
    private final R2StorageService storageService;

    private static final Set<String> VALID_CONTEXTS = Set.of("item", "wishlist");

    /**
     * Upload an image file to R2 storage.
     *
     * @param file    The uploaded file
     * @param context "item" or "wishlist" — determines storage folder
     * @param userId  The authenticated user's ID
     * @return Public URL of the uploaded image
     */
    public String upload(MultipartFile file, String context, String userId) {
        // Validate context
        if (context == null || !VALID_CONTEXTS.contains(context)) {
            throw new ImageUploadException("Context must be 'item' or 'wishlist'");
        }

        // Validate file (size, extension, MIME, magic bytes)
        validationService.validate(file);

        // Generate unique filename
        String extension = validationService.getExtensionForMimeType(file.getContentType());
        String fileId = UUID.randomUUID().toString();
        String key = R2StorageService.buildKey(userId, context, fileId, extension);

        // Upload to R2
        try {
            byte[] data = file.getBytes();
            return storageService.upload(data, key, file.getContentType());
        } catch (IOException e) {
            throw new ImageUploadException("Failed to read file content", e);
        }
    }
}
