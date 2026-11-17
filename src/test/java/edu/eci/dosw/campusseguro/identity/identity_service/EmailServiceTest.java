package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import edu.eci.dosw.campusseguro.identity.identity_service.service.EmailServiceImpl;

/**
 * Tests of the sending of the verification code of the registration (RF-MS01-01) and of the recovery code (RF-MS01-08)
 * by email: recipient, sender, subject and content of the message, and propagation of the failure of the mail server
 */
class EmailServiceTest {

    private static final String FROM = "no-reply@escuelaing.edu.co";
    private static final String TO = "maria.perez@escuelaing.edu.co";

    private JavaMailSender mailSender;
    private EmailServiceImpl emailService;

        /**
     * Prepares the service with a simulated SMTP client
     */
    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailServiceImpl(mailSender, FROM);
    }

        /**
     * Captures the message that is delivered to the SMTP client
     *
     * @return the sent message
     */
    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

        /**
     * The message is sent to the email of the account, from the configured sender and with the subject of the recovery
     */
    @Test
    void sendRecoveryCode_sendsOneMessageToTheAccountFromTheConfiguredSender() {
        emailService.sendRecoveryCode(TO, "482913", 15);

        SimpleMailMessage message = sentMessage();
        assertEquals(FROM, message.getFrom());
        assertArrayEquals(new String[] { TO }, message.getTo());
        assertEquals("Código de recuperación de contraseña de CampusSeguro", message.getSubject());
    }

        /**
     * The body contains the code, its validity in minutes and the warning for whoever did not make the request
     */
    @Test
    void sendRecoveryCode_body_containsTheCodeTheValidityAndTheIgnoreNotice() {
        emailService.sendRecoveryCode(TO, "007391", 15);

        String body = sentMessage().getText();
        assertNotNull(body);
        assertTrue(body.contains("007391"));
        assertTrue(body.contains("15 minutos"));
        assertTrue(body.contains("solo se puede usar una vez"));
        assertTrue(body.contains("Si no hizo esta solicitud"));
    }

        /**
     * The validity that appears in the message is the received one
     */
    @Test
    void sendRecoveryCode_customValidity_isShownInTheBody() {
        emailService.sendRecoveryCode(TO, "123456", 30);

        assertTrue(sentMessage().getText().contains("30 minutos"));
    }

        /**
     * If the mail server fails the error is propagated to the caller, who decides what to do
     */
    @Test
    void sendRecoveryCode_whenTheMailServerFails_propagatesTheMailException() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> emailService.sendRecoveryCode(TO, "482913", 15));
    }

        /**
     * The verification code is sent to the email that is being registered, from the configured sender and with the
     * subject of the registration
     */
    @Test
    void sendRegistrationCode_sendsOneMessageToTheEmailFromTheConfiguredSender() {
        emailService.sendRegistrationCode(TO, "482913", 15);

        SimpleMailMessage message = sentMessage();
        assertEquals(FROM, message.getFrom());
        assertArrayEquals(new String[] { TO }, message.getTo());
        assertEquals("Código de verificación del registro en CampusSeguro", message.getSubject());
    }

        /**
     * The body contains the code, its validity in minutes, that the account is only created with the code and the
     * warning for whoever did not make the request
     */
    @Test
    void sendRegistrationCode_body_containsTheCodeTheValidityAndTheIgnoreNotice() {
        emailService.sendRegistrationCode(TO, "007391", 20);

        String body = sentMessage().getText();
        assertNotNull(body);
        assertTrue(body.contains("007391"));
        assertTrue(body.contains("20 minutos"));
        assertTrue(body.contains("La cuenta solo se crea cuando ingrese este código"));
        assertTrue(body.contains("Si no hizo esta solicitud"));
    }

        /**
     * If the mail server fails the error of the verification code is propagated to the caller
     */
    @Test
    void sendRegistrationCode_whenTheMailServerFails_propagatesTheMailException() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> emailService.sendRegistrationCode(TO, "482913", 15));
    }
}
