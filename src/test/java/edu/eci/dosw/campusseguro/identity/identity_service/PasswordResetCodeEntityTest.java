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
 * Entidad del código de recuperación (tabla codigos_recuperacion): vigencia y correspondencia con las columnas
 * del modelo de datos
 */
class PasswordResetCodeEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");

    /**
     * Construye un código con la expiración y el uso indicados
     *
     * @param expiresAt instante de expiración
     * @param usedAt instante de uso, null si no se ha usado
     * @return el código construido
     */
    private PasswordResetCodeEntity code(Instant expiresAt, Instant usedAt) {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();
        code.setExpiresAt(expiresAt);
        code.setUsedAt(usedAt);
        return code;
    }

    /**
     * Un código sin usar cuya expiración es futura está vigente
     */
    @Test
    void isActiveAt_unusedAndNotExpired_isActive() {
        assertTrue(code(NOW.plusSeconds(1), null).isActiveAt(NOW));
    }

    /**
     * Justo en el instante de expiración el código ya no está vigente
     */
    @Test
    void isActiveAt_atTheExpirationInstant_isNotActive() {
        assertFalse(code(NOW, null).isActiveAt(NOW));
    }

    /**
     * Un código vencido no está vigente
     */
    @Test
    void isActiveAt_expired_isNotActive() {
        assertFalse(code(NOW.minusSeconds(1), null).isActiveAt(NOW));
    }

    /**
     * Un código usado o invalidado no está vigente aunque no haya expirado
     */
    @Test
    void isActiveAt_used_isNotActive() {
        assertFalse(code(NOW.plusSeconds(600), NOW.minusSeconds(1)).isActiveAt(NOW));
    }

    /**
     * Un código nuevo no tiene intentos ni uso
     */
    @Test
    void newCode_hasNoAttemptsAndIsNotUsed() {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();

        assertEquals(0, code.getAttempts());
        assertNull(code.getUsedAt());
        assertNull(code.getId());
    }

    /**
     * Los datos que se asignan se devuelven tal cual
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
     * La entidad se mapea a la tabla y a las columnas del modelo de datos
     *
     * @throws NoSuchFieldException si falta un campo de la entidad
     */
    @Test
    void mapping_matchesTheTableAndColumnsOfTheDataModel() throws NoSuchFieldException {
        assertEquals("codigos_recuperacion", PasswordResetCodeEntity.class.getAnnotation(Table.class).name());
        assertEquals("usuario_id", column("userId"));
        assertEquals("codigo_hash", column("codeHash"));
        assertEquals("intentos", column("attempts"));
        assertEquals("expira_en", column("expiresAt"));
        assertEquals("usado_en", column("usedAt"));
        assertEquals("creado_en", column("createdAt"));
    }

    /**
     * Obtiene el nombre de la columna de un campo de la entidad
     *
     * @param field nombre del campo de la entidad
     * @return el nombre de la columna
     * @throws NoSuchFieldException si el campo no existe
     */
    private String column(String field) throws NoSuchFieldException {
        return PasswordResetCodeEntity.class.getDeclaredField(field).getAnnotation(Column.class).name();
    }
}
