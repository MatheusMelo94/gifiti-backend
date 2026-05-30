package com.gifiti.api.unit;

import com.gifiti.api.config.RateLimitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the new access-code rate-limit bucket added to
 * {@link RateLimitConfig} (T9).
 *
 * <p>Per ADR 0008 § Decision G: 5 tokens, refill 5 / 10 min, keyed by
 * {@code (ip, shareableId)}. Reset-on-success is the responsibility of the
 * caller (service layer) via {@link RateLimitConfig#resetAccessCodeBucket}.
 *
 * <p>Per Security findings F-2 (compound-key correctness), F-3 (rate-limit
 * consumption point + reset semantics), and the dispatch's instruction
 * "Reuse the existing pattern; don't refactor the existing filter."
 */
class RateLimitConfigAccessCodeTest {

    @Test
    @DisplayName("first five attempts from the same (ip, shareableId) are allowed")
    void tryConsumeAccessCodeAllowsFirstFiveAttempts() {
        RateLimitConfig config = new RateLimitConfig();
        for (int i = 1; i <= 5; i++) {
            assertThat(config.tryConsumeAccessCode("192.0.2.1", "share-1"))
                    .as("attempt #%d should be allowed (bucket has 5 tokens initially)", i)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("sixth attempt from same key is rejected (bucket exhausted)")
    void sixthAttemptFromSameKeyIsRejected() {
        RateLimitConfig config = new RateLimitConfig();
        for (int i = 0; i < 5; i++) {
            config.tryConsumeAccessCode("192.0.2.1", "share-1");
        }
        assertThat(config.tryConsumeAccessCode("192.0.2.1", "share-1"))
                .as("6th attempt within window must be rejected (5/10min limit)")
                .isFalse();
    }

    @Test
    @DisplayName("buckets are independent across shareableIds (same IP)")
    void differentShareableIdsAreIndependent() {
        RateLimitConfig config = new RateLimitConfig();
        // Drain bucket for share-1
        for (int i = 0; i < 5; i++) {
            config.tryConsumeAccessCode("192.0.2.1", "share-1");
        }
        // share-2 from same IP should be untouched
        assertThat(config.tryConsumeAccessCode("192.0.2.1", "share-2"))
                .as("(ip, share-2) is a distinct bucket from (ip, share-1)")
                .isTrue();
    }

    @Test
    @DisplayName("buckets are independent across IPs (same shareableId)")
    void differentIpsAreIndependent() {
        RateLimitConfig config = new RateLimitConfig();
        for (int i = 0; i < 5; i++) {
            config.tryConsumeAccessCode("192.0.2.1", "share-1");
        }
        // Different IP on the same wishlist — legitimate co-recipient on
        // a different network should not be locked out by the attacker's
        // attempts. This is the rationale for per-(IP, shareableId) keying
        // per Security findings F-2.
        assertThat(config.tryConsumeAccessCode("198.51.100.1", "share-1"))
                .as("(ip-2, share-1) is a distinct bucket from (ip-1, share-1)")
                .isTrue();
    }

    @Test
    @DisplayName("resetAccessCodeBucket frees the bucket for the (ip, shareableId) key")
    void resetAccessCodeBucketAllowsFreshAttempts() {
        RateLimitConfig config = new RateLimitConfig();
        // Exhaust
        for (int i = 0; i < 5; i++) {
            config.tryConsumeAccessCode("192.0.2.1", "share-1");
        }
        assertThat(config.tryConsumeAccessCode("192.0.2.1", "share-1")).isFalse();

        // Successful validation invalidates the bucket
        config.resetAccessCodeBucket("192.0.2.1", "share-1");

        // 5 fresh tokens
        for (int i = 1; i <= 5; i++) {
            assertThat(config.tryConsumeAccessCode("192.0.2.1", "share-1"))
                    .as("post-reset attempt #%d should be allowed", i)
                    .isTrue();
        }
    }
}
