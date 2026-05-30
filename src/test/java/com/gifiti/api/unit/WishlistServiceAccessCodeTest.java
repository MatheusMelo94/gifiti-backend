package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.analytics.PostHogProperties;
import com.gifiti.api.analytics.WishlistReturnedDedupeCache;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WishlistService} access-code lifecycle behaviors:
 * T3 (generate on PRIVATE create), T4 (visibility transitions).
 *
 * <p>Uses Mockito argument captors to inspect the entity passed to
 * {@code wishlistRepository.save(...)} rather than relying on the mapper's
 * response shape — the test is about the service's mutation of the entity's
 * {@code accessCode}, not about response serialization (T5).
 *
 * <p>Convention citations:
 * <ul>
 *   <li>per ADR 0008 § Decision F — code generated via {@code SecureRandom}
 *       and matches {@code ^\d{4}$};</li>
 *   <li>per ADR 0008 § plan §4.3 — PUBLIC→PRIVATE generates fresh code;
 *       PRIVATE→PUBLIC clears code; PRIVATE→PRIVATE preserves existing code.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceAccessCodeTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private WishlistMapper wishlistMapper;

    @Mock
    private PostHogClient postHogClient;

    @Mock
    private PostHogProperties postHogProperties;

    @Mock
    private WishlistReturnedDedupeCache wishlistReturnedDedupeCache;

    @InjectMocks
    private WishlistService wishlistService;

    @Captor
    private ArgumentCaptor<Wishlist> wishlistCaptor;

    private static final String USER_ID = "user-123";
    private static final String WISHLIST_ID = "wishlist-789";

    @Nested
    @DisplayName("T3 — create() and access-code generation")
    class CreateTests {

        @Test
        @DisplayName("creates PRIVATE wishlist — service sets a 4-digit accessCode before save")
        void createPrivateWishlistGeneratesAccessCode() {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("Birthday 2026")
                    .visibility(Visibility.PRIVATE)
                    .build();

            // Mapper returns a fresh entity (matches WishlistMapper.toEntity
            // shape: ownerUserId + title + visibility, no accessCode).
            Wishlist mapped = Wishlist.builder()
                    .ownerUserId(USER_ID)
                    .title("Birthday 2026")
                    .visibility(Visibility.PRIVATE)
                    .build();
            when(wishlistMapper.toEntity(request, USER_ID)).thenReturn(mapped);
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(wishlistMapper.toResponse(any(Wishlist.class), anyInt()))
                    .thenReturn(WishlistResponse.builder().build());

            wishlistService.create(request, USER_ID);

            // Capture the entity that was actually persisted — its accessCode
            // must be a non-null 4-digit String per ADR 0008 § Decision F.
            org.mockito.Mockito.verify(wishlistRepository).save(wishlistCaptor.capture());
            String generatedCode = wishlistCaptor.getValue().getAccessCode();
            assertThat(generatedCode)
                    .as("PRIVATE wishlist creation must populate accessCode (§ Decision F)")
                    .isNotNull()
                    .matches("^\\d{4}$");
        }

        @Test
        @DisplayName("creates PUBLIC wishlist — service leaves accessCode null")
        void createPublicWishlistDoesNotGenerateAccessCode() {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("Public 2026")
                    .visibility(Visibility.PUBLIC)
                    .build();

            Wishlist mapped = Wishlist.builder()
                    .ownerUserId(USER_ID)
                    .title("Public 2026")
                    .visibility(Visibility.PUBLIC)
                    .build();
            when(wishlistMapper.toEntity(request, USER_ID)).thenReturn(mapped);
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(wishlistMapper.toResponse(any(Wishlist.class), anyInt()))
                    .thenReturn(WishlistResponse.builder().build());

            wishlistService.create(request, USER_ID);

            org.mockito.Mockito.verify(wishlistRepository).save(wishlistCaptor.capture());
            assertThat(wishlistCaptor.getValue().getAccessCode())
                    .as("PUBLIC wishlist creation must NOT set accessCode")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("T4 — update() and visibility transitions")
    class UpdateTransitionTests {

        @Test
        @DisplayName("PUBLIC→PRIVATE generates a fresh accessCode")
        void updatePublicToPrivateGeneratesAccessCode() {
            Wishlist existing = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Wishlist")
                    .visibility(Visibility.PUBLIC)
                    .accessCode(null)
                    .build();
            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .visibility(Visibility.PRIVATE)
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(existing));
            // The real WishlistMapper.updateEntity mutates the entity in-place
            // (partial update). Mimic that here so the service's downstream
            // visibility-transition logic sees the new visibility.
            org.mockito.Mockito.doAnswer(inv -> {
                Wishlist target = inv.getArgument(0);
                UpdateWishlistRequest req = inv.getArgument(1);
                if (req.getTitle() != null) target.setTitle(req.getTitle());
                if (req.getVisibility() != null) target.setVisibility(req.getVisibility());
                return null;
            }).when(wishlistMapper).updateEntity(any(Wishlist.class), any(UpdateWishlistRequest.class));
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(wishlistMapper.toResponse(any(Wishlist.class), anyInt()))
                    .thenReturn(WishlistResponse.builder().build());

            wishlistService.update(WISHLIST_ID, request, USER_ID);

            org.mockito.Mockito.verify(wishlistRepository).save(wishlistCaptor.capture());
            Wishlist saved = wishlistCaptor.getValue();
            assertThat(saved.getVisibility()).isEqualTo(Visibility.PRIVATE);
            assertThat(saved.getAccessCode())
                    .as("PUBLIC→PRIVATE must generate a fresh 4-digit accessCode")
                    .isNotNull()
                    .matches("^\\d{4}$");
        }

        @Test
        @DisplayName("PRIVATE→PUBLIC clears accessCode")
        void updatePrivateToPublicClearsAccessCode() {
            Wishlist existing = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Wishlist")
                    .visibility(Visibility.PRIVATE)
                    .accessCode("1234")
                    .build();
            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .visibility(Visibility.PUBLIC)
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(existing));
            // The real WishlistMapper.updateEntity mutates the entity in-place
            // (partial update). Mimic that here so the service's downstream
            // visibility-transition logic sees the new visibility.
            org.mockito.Mockito.doAnswer(inv -> {
                Wishlist target = inv.getArgument(0);
                UpdateWishlistRequest req = inv.getArgument(1);
                if (req.getTitle() != null) target.setTitle(req.getTitle());
                if (req.getVisibility() != null) target.setVisibility(req.getVisibility());
                return null;
            }).when(wishlistMapper).updateEntity(any(Wishlist.class), any(UpdateWishlistRequest.class));
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(wishlistMapper.toResponse(any(Wishlist.class), anyInt()))
                    .thenReturn(WishlistResponse.builder().build());

            wishlistService.update(WISHLIST_ID, request, USER_ID);

            org.mockito.Mockito.verify(wishlistRepository).save(wishlistCaptor.capture());
            Wishlist saved = wishlistCaptor.getValue();
            assertThat(saved.getVisibility()).isEqualTo(Visibility.PUBLIC);
            assertThat(saved.getAccessCode())
                    .as("PRIVATE→PUBLIC must null out accessCode")
                    .isNull();
        }

        @Test
        @DisplayName("PRIVATE→PRIVATE (e.g. title-only edit) preserves existing accessCode")
        void updatePrivateToPrivateDoesNotChangeAccessCode() {
            Wishlist existing = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Original")
                    .visibility(Visibility.PRIVATE)
                    .accessCode("5678")
                    .build();
            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .title("Renamed")
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(existing));
            // The real WishlistMapper.updateEntity mutates the entity in-place
            // (partial update). Mimic that here so the service's downstream
            // visibility-transition logic sees the new visibility.
            org.mockito.Mockito.doAnswer(inv -> {
                Wishlist target = inv.getArgument(0);
                UpdateWishlistRequest req = inv.getArgument(1);
                if (req.getTitle() != null) target.setTitle(req.getTitle());
                if (req.getVisibility() != null) target.setVisibility(req.getVisibility());
                return null;
            }).when(wishlistMapper).updateEntity(any(Wishlist.class), any(UpdateWishlistRequest.class));
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(wishlistMapper.toResponse(any(Wishlist.class), anyInt()))
                    .thenReturn(WishlistResponse.builder().build());

            wishlistService.update(WISHLIST_ID, request, USER_ID);

            org.mockito.Mockito.verify(wishlistRepository).save(wishlistCaptor.capture());
            Wishlist saved = wishlistCaptor.getValue();
            assertThat(saved.getAccessCode())
                    .as("PRIVATE→PRIVATE title-only edit must NOT change accessCode")
                    .isEqualTo("5678");
        }

        @Test
        @DisplayName("PUBLIC→PUBLIC leaves accessCode null (no-op transition)")
        void updatePublicToPublicLeavesAccessCodeNull() {
            Wishlist existing = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Original")
                    .visibility(Visibility.PUBLIC)
                    .accessCode(null)
                    .build();
            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .title("Renamed")
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(existing));
            // The real WishlistMapper.updateEntity mutates the entity in-place
            // (partial update). Mimic that here so the service's downstream
            // visibility-transition logic sees the new visibility.
            org.mockito.Mockito.doAnswer(inv -> {
                Wishlist target = inv.getArgument(0);
                UpdateWishlistRequest req = inv.getArgument(1);
                if (req.getTitle() != null) target.setTitle(req.getTitle());
                if (req.getVisibility() != null) target.setVisibility(req.getVisibility());
                return null;
            }).when(wishlistMapper).updateEntity(any(Wishlist.class), any(UpdateWishlistRequest.class));
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(wishlistMapper.toResponse(any(Wishlist.class), anyInt()))
                    .thenReturn(WishlistResponse.builder().build());

            wishlistService.update(WISHLIST_ID, request, USER_ID);

            org.mockito.Mockito.verify(wishlistRepository).save(wishlistCaptor.capture());
            assertThat(wishlistCaptor.getValue().getAccessCode()).isNull();
        }
    }
}
