package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new wishlist item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateItemRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @URL(message = "Product link must be a valid URL")
    private String productLink;

    @URL(message = "Image URL must be a valid URL")
    private String imageUrl;

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @Builder.Default
    private Priority priority = Priority.MEDIUM;
}
