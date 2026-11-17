package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.config.RegistrationConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.RegistrationPolicy;

/**
 * Policy of the verification codes of the registration (RN-MS01-44) and its configuration: validity, attempts, wait and
 * daily limit
 */
class RegistrationPolicyTest {

        /**
     * The valid values are kept
     */
    @Test
    void policy_validValues_areKept() {
        RegistrationPolicy policy = new RegistrationPolicy(15, 5, 60, 5);

        assertEquals(15, policy.codeTtlMinutes());
        assertEquals(5, policy.maxAttempts());
        assertEquals(60, policy.resendCooldownSeconds());
        assertEquals(5, policy.maxCodesPerDay());
    }

        /**
     * The wait between codes can be deactivated with zero
     */
    @Test
    void policy_zeroCooldown_isAllowed() {
        assertEquals(0, new RegistrationPolicy(15, 5, 0, 5).resendCooldownSeconds());
    }

        /**
     * A validity of less than one minute leaves the registration unusable and is rejected at startup
     */
    @Test
    void policy_ttlBelowOneMinute_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RegistrationPolicy(0, 5, 60, 5));
    }

        /**
     * Zero allowed attempts leaves the registration unusable and is rejected at startup
     */
    @Test
    void policy_attemptsBelowOne_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RegistrationPolicy(15, 0, 60, 5));
    }

        /**
     * A negative wait makes no sense and is rejected at startup
     */
    @Test
    void policy_negativeCooldown_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RegistrationPolicy(15, 5, -1, 5));
    }

        /**
     * A daily limit of zero codes leaves the registration unusable and is rejected at startup
     */
    @Test
    void policy_dailyLimitBelowOne_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RegistrationPolicy(15, 5, 60, 0));
    }

        /**
     * The configuration creates the policy with the received values
     */
    @Test
    void config_createsThePolicyWithTheConfiguredValues() {
        RegistrationPolicy policy = new RegistrationConfig().registrationPolicy(20, 3, 30, 4);

        assertEquals(new RegistrationPolicy(20, 3, 30, 4), policy);
    }
}
