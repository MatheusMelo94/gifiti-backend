package com.gifiti.api.service;

import com.gifiti.api.dto.response.GifterReservationListResponse;
import com.gifiti.api.dto.response.GifterReservationResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.dto.response.SharedWishlistListResponse;
import com.gifiti.api.dto.response.SharedWishlistResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.Reservation;
import com.gifiti.api.model.User;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for gifter-specific operations.
 * Allows gifters to view and manage their reservations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GifterService {

    private final ReservationRepository reservationRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ReservationService reservationService;

    /**
     * List all reservations for the authenticated gifter.
     * Joins Reservation → WishlistItem → Wishlist to build a rich response.
     */
    public GifterReservationListResponse listReservations(String gifterId) {
        List<Reservation> reservations = reservationRepository.findByReserverId(gifterId);

        List<GifterReservationResponse> responses = reservations.stream()
                .map(this::toGifterReservationResponse)
                .toList();

        return GifterReservationListResponse.builder()
                .reservations(responses)
                .totalCount(responses.size())
                .build();
    }

    /**
     * Cancel the gifter's own reservation.
     * Verifies the reservation belongs to the authenticated gifter before cancelling.
     */
    public ReservationResponse cancelReservation(String itemId, String gifterId) {
        Reservation reservation = reservationRepository.findByItemIdAndReserverId(itemId, gifterId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "itemId", itemId));

        // Delete this gifter's reservation
        reservationRepository.delete(reservation);

        // Decrement the item's reserved quantity
        WishlistItem item = wishlistItemRepository.findById(itemId).orElse(null);
        if (item != null) {
            item.setReservedQuantity(Math.max(0, item.getReservedQuantity() - reservation.getQuantity()));
            if (item.getReservedQuantity() < item.getQuantity()) {
                item.setStatus(ItemStatus.AVAILABLE);
            }
            wishlistItemRepository.save(item);
        }

        return ReservationResponse.unreserved(itemId);
    }

    /**
     * List wishlists where the gifter has at least one reservation.
     * Groups by wishlist and includes reservation count per wishlist.
     */
    public SharedWishlistListResponse listSharedWishlists(String gifterId) {
        log.debug("Finding shared wishlists for gifter: {}", gifterId);

        List<Reservation> reservations = reservationRepository.findByReserverId(gifterId);
        if (reservations.isEmpty()) {
            return SharedWishlistListResponse.builder()
                    .wishlists(List.of())
                    .totalCount(0)
                    .build();
        }

        // Reservation → Item → Wishlist
        List<String> itemIds = reservations.stream()
                .map(Reservation::getItemId)
                .toList();

        Map<String, WishlistItem> itemsById = wishlistItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(WishlistItem::getId, item -> item));

        // Count reservations per wishlist
        Map<String, Long> reservationCountByWishlistId = reservations.stream()
                .map(r -> itemsById.get(r.getItemId()))
                .filter(item -> item != null)
                .collect(Collectors.groupingBy(WishlistItem::getWishlistId, Collectors.counting()));

        List<String> wishlistIds = reservationCountByWishlistId.keySet().stream().toList();
        List<Wishlist> wishlists = wishlistRepository.findByIdIn(wishlistIds);

        List<SharedWishlistResponse> responses = wishlists.stream()
                .map(wishlist -> {
                    int itemCount = wishlistItemRepository.findByWishlistId(wishlist.getId()).size();
                    int myReservationCount = reservationCountByWishlistId
                            .getOrDefault(wishlist.getId(), 0L).intValue();

                    return SharedWishlistResponse.builder()
                            .shareableId(wishlist.getShareableId())
                            .title(wishlist.getTitle())
                            .ownerDisplayName(resolveOwnerDisplayName(wishlist.getOwnerUserId()))
                            .eventDate(wishlist.getEventDate())
                            .itemCount(itemCount)
                            .myReservationCount(myReservationCount)
                            .build();
                })
                .toList();

        log.info("Found {} shared wishlists for gifter: {}", responses.size(), gifterId);

        return SharedWishlistListResponse.builder()
                .wishlists(responses)
                .totalCount(responses.size())
                .build();
    }

    /**
     * Leave a shared wishlist by cancelling all of the gifter's reservations on it.
     * Finds the wishlist by shareableId, then removes all reservations by this gifter
     * on items belonging to that wishlist.
     */
    public void leaveSharedWishlist(String shareableId, String gifterId) {
        log.debug("Gifter {} leaving shared wishlist {}", gifterId, shareableId);

        Wishlist wishlist = wishlistRepository.findByShareableId(shareableId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist", "shareableId", shareableId));

        List<WishlistItem> items = wishlistItemRepository.findByWishlistId(wishlist.getId());
        List<String> itemIds = items.stream().map(WishlistItem::getId).toList();

        // Find this gifter's reservations on these items
        List<Reservation> reservations = reservationRepository.findByReserverId(gifterId).stream()
                .filter(r -> itemIds.contains(r.getItemId()))
                .toList();

        if (reservations.isEmpty()) {
            throw new ResourceNotFoundException("Reservation", "shareableId", shareableId);
        }

        // Cancel each reservation and update item quantities
        for (Reservation reservation : reservations) {
            reservationRepository.delete(reservation);

            WishlistItem item = items.stream()
                    .filter(i -> i.getId().equals(reservation.getItemId()))
                    .findFirst().orElse(null);
            if (item != null) {
                item.setReservedQuantity(Math.max(0, item.getReservedQuantity() - reservation.getQuantity()));
                if (item.getReservedQuantity() < item.getQuantity()) {
                    item.setStatus(ItemStatus.AVAILABLE);
                }
                wishlistItemRepository.save(item);
            }
        }

        log.info("Gifter {} left shared wishlist {}, cancelled {} reservations", gifterId, shareableId, reservations.size());
    }

    private String resolveOwnerDisplayName(String ownerUserId) {
        return userRepository.findById(ownerUserId)
                .map(user -> {
                    if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                        return user.getDisplayName();
                    }
                    return user.getEmail().split("@")[0];
                })
                .orElse("Unknown");
    }

    private GifterReservationResponse toGifterReservationResponse(Reservation reservation) {
        WishlistItem item = wishlistItemRepository.findById(reservation.getItemId()).orElse(null);
        Wishlist wishlist = item != null
                ? wishlistRepository.findById(item.getWishlistId()).orElse(null)
                : null;

        return GifterReservationResponse.builder()
                .itemId(reservation.getItemId())
                .itemName(item != null ? item.getName() : "Unknown")
                .itemImageUrl(item != null ? item.getImageUrl() : null)
                .itemPrice(item != null ? item.getPrice() : null)
                .wishlistTitle(wishlist != null ? wishlist.getTitle() : "Unknown")
                .wishlistShareableId(wishlist != null ? wishlist.getShareableId() : null)
                .eventDate(wishlist != null ? wishlist.getEventDate() : null)
                .reservedAt(reservation.getCreatedAt())
                .build();
    }
}
