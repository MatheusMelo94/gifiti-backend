package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T6 — wishlist_created emission. Server is authoritative on
 * occasion_type (closed enum derived from request DTO) and the item
 * count at creation (typically 0 unless creation flow allows initial items).
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceCreateAnalyticsTest {

    @Mock private WishlistRepository wishlistRepository;
    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private WishlistMapper wishlistMapper;
    @Mock private PostHogClient postHogClient;
    @Mock private com.gifiti.api.analytics.PostHogProperties postHogProperties;
    @Mock private com.gifiti.api.analytics.WishlistReturnedDedupeCache wishlistReturnedDedupeCache;

    @InjectMocks private WishlistService service;

    private static final String USER_ID = "user-007";
    private static final String WISHLIST_ID = "wl-700";

    @BeforeEach
    void wireMapper() {
        when(wishlistMapper.toEntity(any(CreateWishlistRequest.class), anyString()))
                .thenAnswer(inv -> {
                    CreateWishlistRequest req = inv.getArgument(0);
                    return Wishlist.builder()
                            .ownerUserId(inv.getArgument(1))
                            .title(req.getTitle())
                            .category(req.getCategory())
                            .visibility(req.getVisibility())
                            .build();
                });
        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(inv -> {
            Wishlist w = inv.getArgument(0);
            w.setId(WISHLIST_ID);
            return w;
        });
        when(wishlistMapper.toResponse(any(Wishlist.class), eq(0)))
                .thenReturn(WishlistResponse.builder().id(WISHLIST_ID).build());
    }

    @Test
    @DisplayName("create() emits wishlist_created with user_id, occasion_type, item_count_at_creation")
    void emits_wishlist_created() {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title("Birthday list")
                .visibility(Visibility.PRIVATE)
                .category(WishlistCategory.BIRTHDAY)
                .build();

        service.create(request, USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(
                eq("wishlist_created"),
                eq(USER_ID),
                propsCaptor.capture());

        Map<String, Object> props = propsCaptor.getValue();
        assertThat(props).containsEntry("user_id", USER_ID);
        assertThat(props).containsEntry("occasion_type", "BIRTHDAY");
        assertThat(props).containsEntry("item_count_at_creation", 0);
    }

    @Test
    @DisplayName("create() emits with occasion_type=null when category was not set")
    void emits_with_null_category() {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title("No-category list")
                .visibility(Visibility.PRIVATE)
                .build();

        service.create(request, USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(eq("wishlist_created"), eq(USER_ID), propsCaptor.capture());
        // Category is optional in CreateWishlistRequest; an absent value should
        // serialize as a null property — the wrapper allowlist accepts the
        // key, the value can be null without breaking emission.
        assertThat(propsCaptor.getValue()).containsKey("occasion_type");
    }

    @Test
    @DisplayName("create() succeeds even when PostHogClient throws (fail-open, F-6)")
    void posthog_failure_does_not_propagate() {
        doThrow(new RuntimeException("posthog down"))
                .when(postHogClient).capture(anyString(), anyString(), any());

        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title("Resilient")
                .visibility(Visibility.PRIVATE)
                .build();

        assertThatCode(() -> service.create(request, USER_ID))
                .doesNotThrowAnyException();
    }
}
