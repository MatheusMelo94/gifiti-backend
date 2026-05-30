package com.gifiti.api.model;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import com.gifiti.api.validation.SafeUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Wishlist entity representing a gift wishlist.
 * Root aggregate for wishlist management.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wishlists")
public class Wishlist {

    @Id
    private String id;

    @NotNull(message = "Owner user ID is required")
    @Indexed
    private String ownerUserId;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    @Indexed(unique = true)
    @Builder.Default
    private String shareableId = NanoIdUtils.randomNanoId();

    private LocalDate eventDate;

    private WishlistCategory category;

    @SafeUrl
    private String coverImageUrl;

    /**
     * Private-wishlist access code. Per ADR 0008 § Decision E (plaintext at the
     * application layer; encrypted-at-rest via MongoDB Atlas) and § Decision F
     * (4-digit numeric, leading zeros allowed).
     *
     * <p>Semantics: non-null when {@code visibility == PRIVATE}; null when
     * {@code visibility == PUBLIC}. {@link com.gifiti.api.service.WishlistService}
     * is responsible for generating, clearing, and rotating this field
     * (T3 + T4 + T11); the entity itself only stores it.
     *
     * <p>Validation: when non-null, the value must match {@code ^\d{4}$} —
     * enforced both by the {@code @Pattern} annotation at the bean-validation
     * layer and by the {@code AccessCodeGenerator} utility (T2). No index, no
     * unique constraint per ADR 0008 § Decision F.
     *
     * <p>Logging discipline: this field MUST NOT appear in any log line. The
     * {@code SECURITY_EVENT:} unlock-attempt logs (T6) include only
     * {@code correlationId}, {@code shareableId}, and {@code maskedIp}.
     */
    @Pattern(regexp = "^\\d{4}$", message = "Access code must be exactly 4 digits")
    private String accessCode;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
