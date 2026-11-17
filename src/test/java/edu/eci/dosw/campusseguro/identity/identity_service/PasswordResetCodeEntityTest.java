package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

/**
 * Entity of the recovery code (table password_reset_codes): validity and correspondence with the columns of the data
 * model
 */
class PasswordResetCodeEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");

        /**
     * Builds a code with the indicated expiration and use
     *
     * @param expiresAt expiration instant
     * @param usedAt instant of use, null if it has not been used
     * @return the built code
     */
    private PasswordResetCodeEntity code(Instant expiresAt, Instant usedAt) {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();
        code.setExpiresAt(expiresAt);
        code.setUsedAt(usedAt);
        return code;
    }

        /**
     * An unused code whose expiration is in the future is valid
     */
    @Test
    void isActiveAt_unusedAndNotExpired_isActive() {
        assertTrue(code(NOW.plusSeconds(1), null).isActiveAt(NOW));
    }

        /**
     * Right at the expiration instant the code is no longer valid
     */
    @Test
    void isActiveAt_atTheExpirationInstant_isNotActive() {
        assertFalse(code(NOW, null).isActiveAt(NOW));
    }

        /**
     * An expired code is not valid
     */
    @Test
    void isActiveAt_expired_isNotActive() {
        assertFalse(code(NOW.minusSeconds(1), null).isActiveAt(NOW));
    }

        /**
     * A used or invalidated code is not valid even if it has not expired
     */
    @Test
    void isActiveAt_used_isNotActive() {
        assertFalse(code(NOW.plusSeconds(600), NOW.minusSeconds(1)).isActiveAt(NOW));
    }

        /**
     * A new code has no attempts or use
     */
    @Test
    void newCode_hasNoAttemptsAndIsNotUsed() {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();

        assertEquals(0, code.getAttempts());
        assertNull(code.getUsedAt());
        assertNull(code.getId());
    }

        /**
     * The data that is assigned is returned as it is
     */
    @Test
    void setters_areReflectedByTheGetters() {
        UUID userId = UUID.randomUUID();
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();

        code.setUserId(userId);
        code.setCodeHash("$2a$10$hash");
        code.setAttempts(2);
        code.setCreatedAt(NOW);
        code.setExpiresAt(NOW.plusSeconds(900));
        code.setUsedAt(NOW.plusSeconds(10));

        assertEquals(userId, code.getUserId());
        assertEquals("$2a$10$hash", code.getCodeHash());
        assertEquals(2, code.getAttempts());
        assertEquals(NOW, code.getCreatedAt());
        assertEquals(NOW.plusSeconds(900), code.getExpiresAt());
        assertEquals(NOW.plusSeconds(10), code.getUsedAt());
    }

        /**
     * The entity is mapped to the table and the columns of the data model
     *
     * @throws NoSuchFieldException if a field of the entity is missing
     */
    @Test
    void mapping_matchesTheTableAndColumnsOfTheDataModel() throws NoSuchFieldException {
        assertEquals("password_reset_codes", PasswordResetCodeEntity.class.getAnnotation(Table.class).name());
        assertEquals("user_id", column("userId"));
        assertEquals("code_hash", column("codeHash"));
        assertEquals("attempts", column("attempts"));
        assertEquals("expires_at", column("expiresAt"));
        assertEquals("used_at", column("usedAt"));
        assertEquals("created_at", column("createdAt"));
    }

        /**
     * Gets the column name of a field of the entity
     *
     * @param field name of the field of the entity
     * @return the name of the column
     * @throws NoSuchFieldException if the field does not exist
     */
    private String column(String field) throws NoSuchFieldException {
        return PasswordResetCodeEntity.class.getDeclaredField(field).getAnnotation(Column.class).name();
    }
}
