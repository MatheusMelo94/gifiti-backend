package com.gifiti.api.model.enums;

/**
 * Domain enum capturing what user action originated a signup. Optional on
 * {@link com.gifiti.api.dto.request.RegisterRequest}; defaults server-side
 * to {@link #DIRECT} when absent. Surfaced in the {@code signup_completed}
 * PostHog event as the {@code signup_trigger} property — the most
 * strategically-important property in the cofounder's authoritative
 * 7-event taxonomy (plan v2 §1).
 */
public enum SignupTrigger {

    /** Signup originated from the "Create my own wishlist" CTA. */
    CREATED_WISHLIST,

    /** Signup originated from "Reserve this item" on a public wishlist. */
    RESERVED_ITEM,

    /** Top-of-funnel signup — no specific viral context. Default. */
    DIRECT
}
