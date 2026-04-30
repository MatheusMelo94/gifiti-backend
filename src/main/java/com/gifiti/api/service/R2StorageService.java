package com.gifiti.api.service;

import com.gifiti.api.exception.ImageUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Handles file uploads to Cloudflare R2 via the S3-compatible API.
 */
@Slf4j
@Service
public class R2StorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrl;

    public R2StorageService(
            S3Client s3Client,
            @Value("${app.r2.bucket-name}") String bucketName,
            @Value("${app.r2.public-url}") String publicUrl) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicUrl = publicUrl;
    }

    /**
     * Upload a file to R2 and return its public URL.
     */
    public String upload(byte[] data, String key, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentDisposition("inline")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(data));

            String url = publicUrl + "/" + key;
            log.info("Image uploaded to R2: {}", key);
            return url;
        } catch (SdkException e) {
            log.error("Failed to upload image to R2: {} ({})", e.getMessage(), e.getClass().getSimpleName());
            throw new ImageUploadException("Failed to upload image. Please try again.", e);
        }
    }

    /**
     * Build the storage key for an image.
     */
    public static String buildKey(String userId, String context, String fileId, String ext) {
        return "users/" + userId + "/" + context + "s/" + fileId + "." + ext;
    }
}
