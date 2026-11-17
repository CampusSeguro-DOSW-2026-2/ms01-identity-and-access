package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * Global values set by the integration standards (section 1)
 */
class GlobalValuesTest {

        /**
     * The issuer and the audience of the JWT are the global values of the standards
     */
    @Test
    void jwtIssuerAndAudience_areTheOnesOfTheStandards() {
        assertEquals("ms01-identity-and-access", GlobalValues.JWT_ISSUER);
        assertEquals("campusseguro", GlobalValues.JWT_AUDIENCE);
    }

        /**
     * The validity of the JWT is 3600 seconds
     */
    @Test
    void jwtLifetime_is3600Seconds() {
        assertEquals(3600, GlobalValues.JWT_LIFETIME_SECONDS);
    }

        /**
     * The timeout of the HTTP client in the internal calls is 2000 milliseconds
     */
    @Test
    void internalClientTimeout_is2000Milliseconds() {
        assertEquals(2000, GlobalValues.INTERNAL_CLIENT_TIMEOUT_MS);
    }

        /**
     * A user can have at most 50 roles
     */
    @Test
    void maxRolesPerUser_is50() {
        assertEquals(50, GlobalValues.MAX_ROLES_PER_USER);
    }
}
