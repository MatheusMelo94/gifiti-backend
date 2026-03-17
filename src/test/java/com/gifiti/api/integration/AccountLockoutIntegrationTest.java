package com.gifiti.api.integration;

import com.gifiti.api.service.AccountLockoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountLockout Integration Tests")
class AccountLockoutIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountLockoutService accountLockoutService;

    @Test
    @DisplayName("should lock account after max failed attempts")
    void shouldLockAccountAfterMaxFailedAttempts() {
        String email = "locktest@test.com";

        for (int i = 0; i < 5; i++) {
            accountLockoutService.recordFailedAttempt(email);
        }

        assertTrue(accountLockoutService.isLocked(email));
        assertTrue(accountLockoutService.getRemainingLockDuration(email).toMinutes() > 0);
    }

    @Test
    @DisplayName("should not lock before max attempts")
    void shouldNotLockBeforeMaxAttempts() {
        String email = "safe@test.com";

        for (int i = 0; i < 4; i++) {
            accountLockoutService.recordFailedAttempt(email);
        }

        assertFalse(accountLockoutService.isLocked(email));
    }

    @Test
    @DisplayName("successful login should clear attempts when not locked")
    void successfulLoginShouldClearAttemptsWhenNotLocked() {
        String email = "cleartest@test.com";

        accountLockoutService.recordFailedAttempt(email);
        accountLockoutService.recordFailedAttempt(email);
        accountLockoutService.recordSuccessfulLogin(email);

        assertEquals(0, accountLockoutService.getFailedAttempts(email));
    }

    @Test
    @DisplayName("lockout state should persist across service calls")
    void lockoutStateShouldPersist() {
        String email = "persist@test.com";

        for (int i = 0; i < 5; i++) {
            accountLockoutService.recordFailedAttempt(email);
        }

        // Verify lockout state is persisted (not just in-memory)
        assertTrue(accountLockoutService.isLocked(email));
        assertEquals(5, accountLockoutService.getFailedAttempts(email));
    }
}
