package edu.eci.dosw.campusseguro.identity.identity_service.service;

import org.springframework.mail.MailException;

/**
 * Sending of MS01 emails with the configured institutional mail server (RF-MS01-01 and RF-MS01-08)
 */
public interface EmailService {

        /**
     * Sends the password recovery code to the email of the account
     *
     * @param to email to which the code is sent
     * @param code recovery code in clear text, it only exists in memory and in the message
     * @param validMinutes minutes during which the code remains valid
     * @throws MailException if the mail server rejects the message or is not available
     */
    void sendRecoveryCode(String to, String code, int validMinutes);

        /**
     * Sends the verification code of the registration to the email that is being registered
     *
     * @param to email to which the code is sent
     * @param code verification code in clear text, it only exists in memory and in the message
     * @param validMinutes minutes during which the code remains valid
     * @throws MailException if the mail server rejects the message or is not available
     */
    void sendRegistrationCode(String to, String code, int validMinutes);
}
