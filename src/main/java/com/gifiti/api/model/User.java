package com.gifiti.api.model;

import com.gifiti.api.model.enums.AuthProvider;
import com.gifiti.api.model.enums.Language;
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

    @Indexed(unique = true, sparse = true)
    private String googleId;

    private String profilePictureUrl;

    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @NotNull
    @Builder.Default
    private Set<Role> roles = new HashSet<>(Set.of(Role.USER));

    /**
     * The user's preferred UI language for system-generated strings (validation
     * errors, exception messages, success messages, emails).
     *
     * <p>Intentionally nullable with no {@code @Builder.Default} and no
     * {@code @NotNull}: legacy User documents written before the i18n feature
     * shipped do not have this field. Spec criterion #15 + ADR-0001 require
     * those documents to remain readable as-is and to be treated as
     * {@link Language#EN_US} on read without rewriting the document.
     *
     * <p>Read via {@link #effectiveLanguage()}, never directly, when callers
     * need a non-null language.
     */
    private Language preferredLanguage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * The language to use for this user, with the lazy default applied.
     *
     * <p>Returns {@link #preferredLanguage} if set, otherwise {@link Language#EN_US}.
     * This is the only sanctioned way to read a non-null {@link Language} for a
     * user — preserves the spec criterion #15 invariant that a missing
     * {@code preferredLanguage} field is treated as {@code EN_US} without
     * silently rewriting the persisted document.
     */
    public Language effectiveLanguage() {
        return preferredLanguage != null ? preferredLanguage : Language.EN_US;
    }
}
