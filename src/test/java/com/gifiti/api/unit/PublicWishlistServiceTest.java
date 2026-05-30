package com.gifiti.api.unit;

import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.PublicWishlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicWishlistService}, focused on
 * {@code resolveOwnerDisplayName()} after the F-2 mitigation
 * (Security Engineer pre-impl, HIGH).
 *
 * <p>Pre-mitigation, a null/blank {@code displayName} fell back to
 * {@code email.split("@")[0]}, exposing email-prefix PII to anonymous
 * viewers once feature 006 loosens the auth gate. Post-mitigation, the
 * fallback resolves through {@link MessageSource} for key
 * {@code wishlist.owner.anonymous.fallback} ("Wishlist owner" /
 * "Anônimo"), and the email-derived path is gone entirely.
 *
 * <p>The service exposes {@code findByShareableId} as its only public
 * method; we exercise the private fallback through that method to keep
 * the test at the public-API surface (no reflection).
 *
 * <p>Cite: {@code architecture-conventions.md} section Layer Rules,
 * section Backend (Spring Boot) Conventions.
 */
@ExtendWith(MockitoExtension.class)
class PublicWishlistServiceTest {

    private static final String SHAREABLE_ID = "test-shareable-id";
    private static final String OWNER_ID = "owner-user-id-1";
    private static final String OWNER_EMAIL = "maria.santos@gmail.com";
    private static final String FALLBACK_KEY = "wishlist.owner.anonymous.fallback";
    private static final String EN_FALLBACK = "Wishlist owner";
    private static final String PT_FALLBACK = "Anônimo";

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private PublicWishlistService service;

    @BeforeEach
    void setUp() {
        Wishlist wishlist = Wishlist.builder()
                .id("wishlist-1")
                .ownerUserId(OWNER_ID)
                .title("Birthday 2026")
                .visibility(Visibility.PUBLIC)
                .shareableId(SHAREABLE_ID)
                .build();

        // Stubs are lenient because not every test exercises every collaborator
        // (e.g. the not-found-wishlist sanity test short-circuits before the
        // user / item / messageSource lookups).
        lenient().when(wishlistRepository.findByShareableId(SHAREABLE_ID))
                .thenReturn(Optional.of(wishlist));
        lenient().when(wishlistItemRepository.findByWishlistId("wishlist-1"))
                .thenReturn(List.of());

        // MessageSource.getMessage stub: keyed on locale so en-US and pt-BR
        // tests can share the same fixture wiring.
        lenient().when(messageSource.getMessage(eq(FALLBACK_KEY), any(), eq(Locale.US)))
                .thenReturn(EN_FALLBACK);
        lenient().when(messageSource.getMessage(eq(FALLBACK_KEY), any(),
                        eq(Locale.forLanguageTag("pt-BR"))))
                .thenReturn(PT_FALLBACK);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private User ownerWithDisplayName(String displayName) {
        return User.builder()
                .id(OWNER_ID)
                .email(OWNER_EMAIL)
                .displayName(displayName)
                .build();
    }

    @Test
    @DisplayName("resolveOwnerDisplayName returns the displayName when set")
    void returns_displayName_when_set() {
        when(userRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithDisplayName("Maria Silva")));
        LocaleContextHolder.setLocale(Locale.US);

        PublicWishlistResponse response = service.findByShareableId(
                SHAREABLE_ID, Optional.empty(), "127.0.0.1");

        assertThat(response.getOwnerDisplayName()).isEqualTo("Maria Silva");
    }

    @Test
    @DisplayName("resolveOwnerDisplayName returns localized en-US fallback when displayName is null")
    void returns_localized_fallback_when_displayName_null_en_us() {
        when(userRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithDisplayName(null)));
        LocaleContextHolder.setLocale(Locale.US);

        PublicWishlistResponse response = service.findByShareableId(
                SHAREABLE_ID, Optional.empty(), "127.0.0.1");

        assertThat(response.getOwnerDisplayName()).isEqualTo(EN_FALLBACK);
    }

    @Test
    @DisplayName("resolveOwnerDisplayName returns localized fallback when displayName is blank/whitespace")
    void returns_localized_fallback_when_displayName_blank() {
        when(userRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithDisplayName("   ")));
        LocaleContextHolder.setLocale(Locale.US);

        PublicWishlistResponse response = service.findByShareableId(
                SHAREABLE_ID, Optional.empty(), "127.0.0.1");

        assertThat(response.getOwnerDisplayName()).isEqualTo(EN_FALLBACK);
    }

    @Test
    @DisplayName("resolveOwnerDisplayName returns pt-BR fallback when locale is pt-BR")
    void returns_pt_BR_fallback_when_locale_is_pt_BR() {
        when(userRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithDisplayName(null)));
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));

        PublicWishlistResponse response = service.findByShareableId(
                SHAREABLE_ID, Optional.empty(), "127.0.0.1");

        assertThat(response.getOwnerDisplayName()).isEqualTo(PT_FALLBACK);
    }

    @Test
    @DisplayName("F-2 regression guard: resolveOwnerDisplayName NEVER returns an email-derived value")
    void never_returns_email_derived_value() {
        when(userRepository.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithDisplayName(null)));
        LocaleContextHolder.setLocale(Locale.US);

        PublicWishlistResponse response = service.findByShareableId(
                SHAREABLE_ID, Optional.empty(), "127.0.0.1");

        // The owner's email is "maria.santos@gmail.com" — pre-mitigation the
        // service returned "maria.santos". This regression guard ensures no
        // future refactor reintroduces the email-prefix path.
        assertThat(response.getOwnerDisplayName())
                .as("F-2: owner displayName fallback must not contain the email local-part")
                .doesNotContain("maria.santos")
                .doesNotContain("@");
    }

    @Test
    @DisplayName("resolveOwnerDisplayName falls back to localized string when owner is missing entirely")
    void returns_localized_fallback_when_owner_not_found() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());
        LocaleContextHolder.setLocale(Locale.US);

        PublicWishlistResponse response = service.findByShareableId(
                SHAREABLE_ID, Optional.empty(), "127.0.0.1");

        assertThat(response.getOwnerDisplayName()).isEqualTo(EN_FALLBACK);
    }

    @Test
    @DisplayName("findByShareableId throws when wishlist not found (sanity)")
    void throws_when_wishlist_not_found() {
        when(wishlistRepository.findByShareableId("missing")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.findByShareableId("missing", Optional.empty(), "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
