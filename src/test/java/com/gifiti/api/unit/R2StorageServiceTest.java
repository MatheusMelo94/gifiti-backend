package com.gifiti.api.unit;

import com.gifiti.api.exception.ImageUploadException;
import com.gifiti.api.service.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private R2StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new R2StorageService(s3Client, "test-bucket", "https://test-public.r2.dev");
    }

    @Test
    @DisplayName("should upload file and return public URL")
    void shouldUploadAndReturnUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] data = new byte[]{1, 2, 3};
        String key = "users/user123/items/abc-def.jpg";

        String url = storageService.upload(data, key, "image/jpeg");

        assertThat(url).isEqualTo("https://test-public.r2.dev/users/user123/items/abc-def.jpg");
    }

    @Test
    @DisplayName("should set correct Content-Type and Content-Disposition")
    void shouldSetCorrectHeaders() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.upload(new byte[]{1}, "users/u1/items/test.png", "image/png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo("users/u1/items/test.png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentDisposition()).isEqualTo("inline");
    }

    @Test
    @DisplayName("should throw ImageUploadException on S3 error")
    void shouldThrowOnS3Error() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Connection refused").build());

        assertThatThrownBy(() -> storageService.upload(new byte[]{1}, "key", "image/jpeg"))
                .isInstanceOf(ImageUploadException.class)
                // Task 10: getMessage() now returns the i18n key (keyed-constructor
                // contract); GlobalExceptionHandler resolves it to English at response time.
                .hasMessage("error.image.upload.failed");
    }

    @Test
    @DisplayName("should build correct storage key")
    void shouldBuildCorrectKey() {
        String key = R2StorageService.buildKey("user123", "item", "abc-def", "jpg");
        assertThat(key).isEqualTo("users/user123/items/abc-def.jpg");
    }

    @Test
    @DisplayName("should build correct storage key for wishlist context")
    void shouldBuildCorrectKeyForWishlist() {
        String key = R2StorageService.buildKey("user456", "wishlist", "xyz-123", "png");
        assertThat(key).isEqualTo("users/user456/wishlists/xyz-123.png");
    }
}
