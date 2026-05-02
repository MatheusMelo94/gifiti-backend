package com.gifiti.api.unit;

import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.ImageUploadException;
import com.gifiti.api.exception.LocalizedRuntimeException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link LocalizedRuntimeException} base class introduced
 * in Task 4 of the i18n backend feature, plus targeted round-trip tests for
 * each business exception subclass.
 *
 * Scope (per spec/005-i18n-backend-support/tasks.md § Task 4):
 * - Base class carries (messageKey, args), exposes them via getMessageKey() / getArgs().
 * - getMessage() returns the key for keyed construction (operator-facing logs see the key).
 * - The legacy single-String constructor remains functional but is @Deprecated, so
 *   existing call sites continue to compile until Task 10 migrates them.
 * - Each of the 5 business exceptions extends LocalizedRuntimeException, supports the
 *   keyed constructor, retains its @ResponseStatus, and keeps its legacy signatures.
 */
class LocalizedRuntimeExceptionTest {

    @Nested
    @DisplayName("LocalizedRuntimeException base class")
    class BaseClass {

        @Test
        @DisplayName("keyed constructor with explicit empty args array stores key and empty args")
        void construction_with_key_and_no_args_stores_key() {
            // Single-String calls resolve to the deprecated literal constructor by
            // JLS 15.12.2.2 (fixed arity wins over varargs in phase 1). For keyed
            // construction without args, pass an explicit empty array. In practice,
            // every keyed call site has at least one arg (see subclass tests below),
            // so this branch is here primarily to pin the contract.
            LocalizedRuntimeException ex = new LocalizedRuntimeException(
                    "error.test.key", new Object[0]);

            assertThat(ex.getMessageKey()).isEqualTo("error.test.key");
            assertThat(ex.getArgs()).isNotNull();
            assertThat(ex.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("keyed constructor with args stores both key and args")
        void construction_with_key_and_args_stores_both() {
            LocalizedRuntimeException ex = new LocalizedRuntimeException(
                    "error.resource.not.found.with.field",
                    "Wishlist", "id", "abc123");

            assertThat(ex.getMessageKey()).isEqualTo("error.resource.not.found.with.field");
            assertThat(ex.getArgs())
                    .containsExactly("Wishlist", "id", "abc123");
        }

        @Test
        @DisplayName("getMessage() returns the key when constructed via keyed form (operator-facing default)")
        void getMessage_returns_key_for_logging() {
            LocalizedRuntimeException ex = new LocalizedRuntimeException(
                    "error.user.not.found", "alice@example.com");

            // Per spec line 107: getMessage() returns the key as defensive fallback.
            // Keeps logs deterministic and English-only (spec § Out of Scope #11).
            assertThat(ex.getMessage()).isEqualTo("error.user.not.found");
        }

        @Test
        @DisplayName("legacy single-String constructor preserves the literal message and yields null messageKey")
        void legacy_constructor_with_literal_message_still_works() {
            @SuppressWarnings("deprecation")
            LocalizedRuntimeException ex = new LocalizedRuntimeException("Some literal English message");

            assertThat(ex.getMessage()).isEqualTo("Some literal English message");
            assertThat(ex.getMessageKey()).isNull();
            assertThat(ex.getArgs()).isNotNull();
            assertThat(ex.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("keyed constructor with cause preserves cause and key")
        void keyed_constructor_with_cause_preserves_both() {
            Throwable rootCause = new RuntimeException("root");
            LocalizedRuntimeException ex = new LocalizedRuntimeException(
                    "error.image.upload.failed",
                    new Object[]{"avatar.png"},
                    rootCause);

            assertThat(ex.getMessageKey()).isEqualTo("error.image.upload.failed");
            assertThat(ex.getArgs()).containsExactly("avatar.png");
            assertThat(ex.getCause()).isSameAs(rootCause);
        }
    }

    @Nested
    @DisplayName("ResourceNotFoundException subclass")
    class ResourceNotFound {

        @Test
        @DisplayName("extends LocalizedRuntimeException")
        void extends_localized_runtime_exception() {
            assertThat(new ResourceNotFoundException("error.user.not.found", "alice@example.com"))
                    .isInstanceOf(LocalizedRuntimeException.class);
        }

        @Test
        @DisplayName("keyed constructor stores key and args")
        void keyed_constructor_works() {
            ResourceNotFoundException ex = new ResourceNotFoundException(
                    "error.user.not.found", "alice@example.com");

            assertThat(ex.getMessageKey()).isEqualTo("error.user.not.found");
            assertThat(ex.getArgs()).containsExactly("alice@example.com");
        }

        @Test
        @DisplayName("legacy 3-arg structured constructor delegates to error.resource.not.found.with.field with args")
        void legacy_three_arg_constructor_maps_to_keyed_form() {
            @SuppressWarnings("deprecation")
            ResourceNotFoundException ex = new ResourceNotFoundException("User", "email", "alice@example.com");

            assertThat(ex.getMessageKey()).isEqualTo("error.resource.not.found.with.field");
            assertThat(ex.getArgs()).containsExactly("User", "email", "alice@example.com");
        }

        @Test
        @DisplayName("legacy single-String constructor still compiles and yields literal message")
        void legacy_single_string_constructor_still_works() {
            @SuppressWarnings("deprecation")
            ResourceNotFoundException ex = new ResourceNotFoundException("Some literal");

            assertThat(ex.getMessage()).isEqualTo("Some literal");
            assertThat(ex.getMessageKey()).isNull();
        }

        @Test
        @DisplayName("retains @ResponseStatus(NOT_FOUND)")
        void retains_response_status_404() {
            ResponseStatus rs = ResourceNotFoundException.class.getAnnotation(ResponseStatus.class);
            assertThat(rs).isNotNull();
            assertThat(rs.value()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("UnauthorizedException subclass")
    class Unauthorized {

        @Test
        @DisplayName("extends LocalizedRuntimeException and supports keyed constructor")
        void keyed_constructor_works() {
            // Single-string calls resolve to the deprecated legacy ctor; pass an
            // explicit empty Object[] for keyed-no-args.
            UnauthorizedException ex = new UnauthorizedException(
                    "error.auth.invalid.credentials", new Object[0]);

            assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
            assertThat(ex.getMessageKey()).isEqualTo("error.auth.invalid.credentials");
            assertThat(ex.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("legacy no-arg and single-String constructors still work")
        void legacy_constructors_still_work() {
            @SuppressWarnings("deprecation")
            UnauthorizedException withDefault = new UnauthorizedException();
            assertThat(withDefault.getMessage()).isEqualTo("Authentication required");

            @SuppressWarnings("deprecation")
            UnauthorizedException withLiteral = new UnauthorizedException("Token expired");
            assertThat(withLiteral.getMessage()).isEqualTo("Token expired");
            assertThat(withLiteral.getMessageKey()).isNull();
        }

        @Test
        @DisplayName("retains @ResponseStatus(UNAUTHORIZED)")
        void retains_response_status_401() {
            ResponseStatus rs = UnauthorizedException.class.getAnnotation(ResponseStatus.class);
            assertThat(rs).isNotNull();
            assertThat(rs.value()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("AccessDeniedException subclass")
    class AccessDenied {

        @Test
        @DisplayName("extends LocalizedRuntimeException and supports keyed constructor")
        void keyed_constructor_works() {
            // Single-string calls resolve to the deprecated legacy ctor; pass an
            // explicit empty Object[] for keyed-no-args.
            AccessDeniedException ex = new AccessDeniedException(
                    "error.access.denied.wishlist", new Object[0]);

            assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
            assertThat(ex.getMessageKey()).isEqualTo("error.access.denied.wishlist");
        }

        @Test
        @DisplayName("legacy no-arg and single-String constructors still work")
        void legacy_constructors_still_work() {
            @SuppressWarnings("deprecation")
            AccessDeniedException withDefault = new AccessDeniedException();
            assertThat(withDefault.getMessage()).isEqualTo("Access denied");

            @SuppressWarnings("deprecation")
            AccessDeniedException withLiteral = new AccessDeniedException("Forbidden for role");
            assertThat(withLiteral.getMessage()).isEqualTo("Forbidden for role");
            assertThat(withLiteral.getMessageKey()).isNull();
        }

        @Test
        @DisplayName("retains @ResponseStatus(FORBIDDEN)")
        void retains_response_status_403() {
            ResponseStatus rs = AccessDeniedException.class.getAnnotation(ResponseStatus.class);
            assertThat(rs).isNotNull();
            assertThat(rs.value()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("ConflictException subclass")
    class Conflict {

        @Test
        @DisplayName("extends LocalizedRuntimeException and supports keyed constructor")
        void keyed_constructor_works() {
            ConflictException ex = new ConflictException(
                    "error.reservation.duplicate", "itemId-123");

            assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
            assertThat(ex.getMessageKey()).isEqualTo("error.reservation.duplicate");
            assertThat(ex.getArgs()).containsExactly("itemId-123");
        }

        @Test
        @DisplayName("legacy single-String constructor still works")
        void legacy_constructor_still_works() {
            @SuppressWarnings("deprecation")
            ConflictException ex = new ConflictException("Already reserved");
            assertThat(ex.getMessage()).isEqualTo("Already reserved");
            assertThat(ex.getMessageKey()).isNull();
        }

        @Test
        @DisplayName("retains @ResponseStatus(CONFLICT)")
        void retains_response_status_409() {
            ResponseStatus rs = ConflictException.class.getAnnotation(ResponseStatus.class);
            assertThat(rs).isNotNull();
            assertThat(rs.value()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("ImageUploadException subclass")
    class ImageUpload {

        @Test
        @DisplayName("extends LocalizedRuntimeException and supports keyed constructor")
        void keyed_constructor_works() {
            ImageUploadException ex = new ImageUploadException(
                    "error.image.upload.failed", "avatar.png");

            assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
            assertThat(ex.getMessageKey()).isEqualTo("error.image.upload.failed");
            assertThat(ex.getArgs()).containsExactly("avatar.png");
        }

        @Test
        @DisplayName("keyed constructor with cause preserves both key/args and cause")
        void keyed_constructor_with_cause_works() {
            Throwable rootCause = new RuntimeException("R2 down");
            ImageUploadException ex = new ImageUploadException(
                    "error.image.upload.failed",
                    new Object[]{"avatar.png"},
                    rootCause);

            assertThat(ex.getMessageKey()).isEqualTo("error.image.upload.failed");
            assertThat(ex.getArgs()).containsExactly("avatar.png");
            assertThat(ex.getCause()).isSameAs(rootCause);
        }

        @Test
        @DisplayName("legacy (message) and (message, cause) constructors still work")
        void legacy_constructors_still_work() {
            @SuppressWarnings("deprecation")
            ImageUploadException byMessage = new ImageUploadException("Image too large");
            assertThat(byMessage.getMessage()).isEqualTo("Image too large");
            assertThat(byMessage.getMessageKey()).isNull();

            Throwable rootCause = new RuntimeException("io fail");
            @SuppressWarnings("deprecation")
            ImageUploadException byMessageAndCause = new ImageUploadException("R2 PUT failed", rootCause);
            assertThat(byMessageAndCause.getMessage()).isEqualTo("R2 PUT failed");
            assertThat(byMessageAndCause.getMessageKey()).isNull();
            assertThat(byMessageAndCause.getCause()).isSameAs(rootCause);
        }

        @Test
        @DisplayName("retains @ResponseStatus(BAD_REQUEST)")
        void retains_response_status_400() {
            ResponseStatus rs = ImageUploadException.class.getAnnotation(ResponseStatus.class);
            assertThat(rs).isNotNull();
            assertThat(rs.value()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
