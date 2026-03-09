package com.gifiti.api.model;

import com.gifiti.api.model.enums.Role;
import jakarta.validation.constraints.Email;
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
import java.util.HashSet;
import java.util.Set;

/**
 * User entity representing a registered user.
 * Root aggregate for user management.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @Size(max = 50, message = "Display name must not exceed 50 characters")
    private String displayName;

    @Builder.Default
    private boolean emailVerified = false;

    @Indexed(unique = true, sparse = true)
    private String verificationToken;
    private Instant verificationTokenExpiry;

    @Indexed(unique = true, sparse = true)
    private String passwordResetToken;
    private Instant passwordResetTokenExpiry;

    @NotNull
    @Builder.Default
    private Set<Role> roles = new HashSet<>(Set.of(Role.USER));

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
