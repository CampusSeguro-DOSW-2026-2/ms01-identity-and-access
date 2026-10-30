package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Código de un solo uso para restablecer la contraseña (tabla codigos_recuperacion del modelo de datos,
 * RF-MS01-08), el código en claro nunca se guarda: solo su hash
 *
 * Un código está vigente mientras no se haya usado y no haya expirado, un código usado es un código
 * consumido o invalidado
 */
@Entity
@Table(name = "codigos_recuperacion")
public class PasswordResetCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "intentos", nullable = false)
    private int attempts;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "usado_en")
    private Instant usedAt;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Devuelve el identificador del código
     *
     * @return el id del código, null si todavía no se guardó
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Devuelve el identificador del usuario dueño del código
     *
     * @return el id del usuario
     */
    public UUID getUserId() {
        return this.userId;
    }

    /**
     * Coloca el identificador del usuario dueño del código
     *
     * @param userId id del usuario
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    /**
     * Devuelve el hash del código de seis dígitos
     *
     * @return el hash del código, nunca el código en claro
     */
    public String getCodeHash() {
        return this.codeHash;
    }

    /**
     * Coloca el hash del código de seis dígitos
     *
     * @param codeHash hash del código
     */
    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    /**
     * Devuelve la cantidad de intentos fallidos de verificación del código
     *
     * @return los intentos fallidos acumulados
     */
    public int getAttempts() {
        return this.attempts;
    }

    /**
     * Coloca la cantidad de intentos fallidos de verificación del código
     *
     * @param attempts intentos fallidos acumulados
     */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    /**
     * Devuelve el instante en el que el código deja de ser válido
     *
     * @return el instante de expiración
     */
    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    /**
     * Coloca el instante en el que el código deja de ser válido
     *
     * @param expiresAt instante de expiración
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Devuelve el instante en el que el código se consumió o se invalidó
     *
     * @return el instante de uso, null si el código no se ha usado
     */
    public Instant getUsedAt() {
        return this.usedAt;
    }

    /**
     * Coloca el instante en el que el código se consumió o se invalidó
     *
     * @param usedAt instante de uso, null si el código no se ha usado
     */
    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    /**
     * Devuelve el instante de creación del código
     *
     * @return el instante en el que se generó el código
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Coloca el instante de creación del código
     *
     * @param createdAt instante en el que se generó el código
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Indica si el código está vigente en un instante, es decir si no se ha usado y todavía no expira
     *
     * @param now instante contra el que se evalúa la vigencia
     * @return true si el código no se ha usado y su expiración es posterior al instante
     */
    public boolean isActiveAt(Instant now) {
        if (this.usedAt != null) {
            return false;
        }
        return this.expiresAt.isAfter(now);
    }
}
