package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PendingRegistrationEntity;

/**
 * Tests of the validity of the code of a pending registration (RN-MS01-44)
 */
class PendingRegistrationEntityTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

        /**
     * Builds a pending registration whose code expires at the indicated instant
     *
     * @param expiresAt expiration of the code
     * @return the pending registration
     */
    private PendingRegistrationEntity expiringAt(Instant expiresAt) {
        PendingRegistrationEntity pending = new PendingRegistrationEntity();
        pending.setExpiresAt(expiresAt);
        return pending;
    }

        /**
     * A code that has not expired is active
     */
    @Test
    void isCodeActiveAt_beforeTheExpiration_isActive() {
        assertTrue(expiringAt(NOW.plusSeconds(1)).isCodeActiveAt(NOW));
    }

        /**
     * A code is no longer active at the exact instant of its expiration, which is how an invalidated code is left
     */
    @Test
    void isCodeActiveAt_atTheExpiration_isNotActive() {
        assertFalse(expiringAt(NOW).isCodeActiveAt(NOW));
    }

        /**
     * An expired code is not active
     */
    @Test
    void isCodeActiveAt_afterTheExpiration_isNotActive() {
        assertFalse(expiringAt(NOW.minusSeconds(1)).isCodeActiveAt(NOW));
    }
}
