package com.gifiti.api.controller;

import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.service.PublicWishlistService;
import com.gifiti.api.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for public (unauthenticated) wishlist access.
 * Allows anyone with a shareable link to view a public wishlist and reserve items.
 *
 * Security: This controller is whitelisted in SecurityConfig:
 * - GET /api/v1/public/** is permitted for viewing
 * - POST /api/v1/public/wishlists/{id}/items/{id}/reserve is permitted for reservations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/wishlists")
@RequiredArgsConstructor
public class PublicWishlistController {

    private final PublicWishlistService publicWishlistService;
    private final ReservationService reservationService;

    /**
     * View a public wishlist by its shareable ID.
     * No authentication required.
     *
     * GET /api/v1/public/wishlists/{shareableId}
     *
     * @param shareableId The shareable identifier (NanoID)
     * @return Wishlist details with items
     */
    @GetMapping("/{shareableId}")
    public ResponseEntity<PublicWishlistResponse> getPublicWishlist(
            @PathVariable String shareableId) {
        log.debug("Public wishlist request for: {}", shareableId);
        PublicWishlistResponse response = publicWishlistService.findByShareableId(shareableId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reserve an item on a public wishlist.
     * No authentication required - uses session/IP for reserver identity.
     *
     * POST /api/v1/public/wishlists/{shareableId}/items/{itemId}/reserve
     *
     * @param shareableId The shareable identifier (NanoID)
     * @param itemId The item to reserve
     * @param request HTTP request for extracting reserver identity
     * @return Reservation confirmation
     */
    @PostMapping("/{shareableId}/items/{itemId}/reserve")
    public ResponseEntity<ReservationResponse> reserveItem(
            @PathVariable String shareableId,
            @PathVariable String itemId,
            HttpServletRequest request) {
        log.debug("Reserve request for item {} in wishlist {}", itemId, shareableId);

        // Verify wishlist exists and is public (throws 404 if not)
        publicWishlistService.findByShareableId(shareableId);

        // Generate anonymous reserver ID from session or IP
        String reserverId = getReserverId(request);

        ReservationResponse response = reservationService.reserve(itemId, reserverId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate an anonymous reserver identifier without creating sessions.
     *
     * Security: Uses stateless fingerprinting (IP + User-Agent hash) to maintain
     * STATELESS design and prevent session-based DoS attacks.
     * This identifier is stable per client but not trackable across IP changes.
     */
    private String getReserverId(HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String fingerprint = ip + ":" + (userAgent != null ? userAgent.hashCode() : "unknown");
        return "fingerprint:" + Integer.toHexString(fingerprint.hashCode());
    }

    /**
     * Extract client IP with X-Forwarded-For spoofing protection.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Only trust X-Forwarded-For if from private network (proxy)
        if (!isPrivateNetwork(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!isPrivateNetwork(ip)) {
                    return ip;
                }
            }
        }
        return remoteAddr;
    }

    private boolean isPrivateNetwork(String ip) {
        return ip.startsWith("10.") ||
               ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") || ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") || ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") || ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") || ip.startsWith("172.31.") ||
               ip.startsWith("192.168.") ||
               ip.equals("127.0.0.1") ||
               ip.equals("0:0:0:0:0:0:0:1");
    }
}
