package edu.eci.dosw.campusseguro.identity.identity_service.util;

/**
 * Global values set by the Integration Standards (section 1), they are the same in all environments and that is why
 * they are defined in the code and not read from the configuration
 */
public final class GlobalValues {

    public static final String JWT_ISSUER = "ms01-identity-and-access";
    public static final String JWT_AUDIENCE = "campusseguro";
    public static final long JWT_LIFETIME_SECONDS = 3600;
    public static final long INTERNAL_CLIENT_TIMEOUT_MS = 2000;
    public static final int MAX_ROLES_PER_USER = 50;
    public static final long SESSION_CUT_CLOCK_SKEW_SECONDS = 30;
    public static final long SESSION_CUT_MARGIN_SECONDS = 70;
    public static final long SESSION_CUT_WINDOW_SECONDS = JWT_LIFETIME_SECONDS + SESSION_CUT_CLOCK_SKEW_SECONDS
            + SESSION_CUT_MARGIN_SECONDS;

        /**
     * Utility class, it is not instantiated
     */
    private GlobalValues() {
    }
}
