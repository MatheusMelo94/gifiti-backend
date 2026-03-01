package com.gifiti.api.model;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.gifiti.api.model.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
