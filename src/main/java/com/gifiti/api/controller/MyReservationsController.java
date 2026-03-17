package com.gifiti.api.controller;

import com.gifiti.api.dto.response.GifterReservationListResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.service.GifterService;
import com.gifiti.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing the authenticated user's reservations.
 * Any logged-in user can view and cancel their own reservations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reservations/mine")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "My Reservations", description = "View and manage your gift reservations")
public class MyReservationsController {

    private final GifterService gifterService;
    private final UserService userService;

    @Operation(summary = "List all your reservations across all wishlists")
    @GetMapping
    public ResponseEntity<GifterReservationListResponse> listMyReservations(Authentication authentication) {
        String userId = resolveUserId(authentication.getName());
        GifterReservationListResponse response = gifterService.listReservations(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel your reservation on an item")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable String itemId,
            Authentication authentication) {
        String userId = resolveUserId(authentication.getName());
        ReservationResponse response = gifterService.cancelReservation(itemId, userId);
        return ResponseEntity.ok(response);
    }

    private String resolveUserId(String email) {
        return userService.getUserIdByEmail(email);
    }
}
