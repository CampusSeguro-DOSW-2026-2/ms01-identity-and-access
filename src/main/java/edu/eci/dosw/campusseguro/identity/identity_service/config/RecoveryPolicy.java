package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.time.Duration;

/**
 * Política de los códigos de recuperación de contraseña (RF-MS01-08, RN-MS01-21), se ajusta con variables de
 * entorno y se valida al arrancar la aplicación
 *
 * @param codeTtlMinutes minutos de vigencia de un código desde que se genera, mínimo 1
 * @param maxAttempts intentos fallidos permitidos por código antes de invalidarlo, mínimo 1
 * @param resendCooldownSeconds segundos que deben pasar entre la generación de un código y la del siguiente,
 *        0 desactiva la espera
 */
public record RecoveryPolicy(int codeTtlMinutes, int maxAttempts, int resendCooldownSeconds) {

    /**
     * Rechaza valores que dejarían la recuperación inutilizable o sin ninguna protección
     *
     * @throws IllegalArgumentException si la vigencia o los intentos son menores que 1 o la espera es negativa
     */
    public RecoveryPolicy {
        if (codeTtlMinutes < 1) {
            throw new IllegalArgumentException("recovery.code-ttl-minutes debe ser al menos 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("recovery.max-attempts debe ser al menos 1");
        }
        if (resendCooldownSeconds < 0) {
            throw new IllegalArgumentException("recovery.resend-cooldown-seconds no puede ser negativo");
        }
    }

    /**
     * Devuelve la vigencia de un código como duración
     *
     * @return el tiempo que un código permanece vigente
     */
    public Duration codeTtl() {
        return Duration.ofMinutes(codeTtlMinutes);
    }

    /**
     * Devuelve el tiempo mínimo entre la generación de dos códigos del mismo usuario
     *
     * @return la espera entre solicitudes, cero si está desactivada
     */
    public Duration resendCooldown() {
        return Duration.ofSeconds(resendCooldownSeconds);
    }
}
