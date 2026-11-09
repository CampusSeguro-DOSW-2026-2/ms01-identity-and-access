package edu.eci.dosw.campusseguro.identity.identity_service.util;

/**
 * Valores globales que fijan los Estándares de Integración (sección 1), son iguales en todos los ambientes
 * y por eso se definen en el código y no se leen de la configuración
 */
public final class GlobalValues {

    public static final String JWT_ISSUER = "ms01-identity-and-access";
    public static final String JWT_AUDIENCE = "campusseguro";
    public static final long JWT_LIFETIME_SECONDS = 3600;
    public static final long INTERNAL_CLIENT_TIMEOUT_MS = 2000;

    /**
     * Clase de utilidad, no se instancia
     */
    private GlobalValues() {
    }
}
