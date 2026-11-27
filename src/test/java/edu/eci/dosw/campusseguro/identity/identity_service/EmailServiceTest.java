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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

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
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        emailService = new EmailServiceImpl(mailSender, FROM);
    }

        /**
     * Captures the message that is delivered to the SMTP client
     *
     * @return the sent message
     */
    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

        /**
     * Collects the parts of the sent message (plain text, HTML and inline logo)
     *
     * @return the leaf parts of the message
     */
    private List<Part> parts() throws MessagingException, IOException {
        MimeMessage message = sentMessage();
        message.saveChanges();
        List<Part> parts = new ArrayList<>();
        collect(message, parts);
        return parts;
    }

    private static void collect(Part part, List<Part> parts) throws MessagingException, IOException {
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, parts);
            }
        } else {
            parts.add(part);
        }
    }

        /**
     * Content of the part of the given type
     *
     * @param type MIME type
     * @return the text of the part
     */
    private String content(String type) throws MessagingException, IOException {
        for (Part part : parts()) {
            if (part.isMimeType(type)) {
                return (String) part.getContent();
            }
        }
        return null;
    }

        /**
     * The message is sent to the email of the account, from the configured sender and with the subject of the recovery
     */
    @Test
    void sendRecoveryCode_sendsOneMessageToTheAccountFromTheConfiguredSender() throws MessagingException {
        emailService.sendRecoveryCode(TO, "482913", 15);

        MimeMessage message = sentMessage();
        assertEquals(FROM, ((InternetAddress) message.getFrom()[0]).getAddress());
        assertArrayEquals(new InternetAddress[] { new InternetAddress(TO) }, message.getAllRecipients());
        assertEquals("Código de recuperación de contraseña de CampusSeguro", message.getSubject());
    }

        /**
     * The body contains the code, its validity in minutes and the warning for whoever did not make the request
     */
    @Test
    void sendRecoveryCode_body_containsTheCodeTheValidityAndTheIgnoreNotice() throws MessagingException, IOException {
        emailService.sendRecoveryCode(TO, "007391", 15);

        String body = content("text/plain");
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
    void sendRecoveryCode_customValidity_isShownInTheBody() throws MessagingException, IOException {
        emailService.sendRecoveryCode(TO, "123456", 30);

        assertTrue(content("text/plain").contains("30 minutos"));
    }

        /**
     * If the mail server fails the error is propagated to the caller, who decides what to do
     */
    @Test
    void sendRecoveryCode_whenTheMailServerFails_propagatesTheMailException() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> emailService.sendRecoveryCode(TO, "482913", 15));
    }

        /**
     * The verification code is sent to the email that is being registered, from the configured sender and with the
     * subject of the registration
     */
    @Test
    void sendRegistrationCode_sendsOneMessageToTheEmailFromTheConfiguredSender() throws MessagingException {
        emailService.sendRegistrationCode(TO, "482913", 15);

        MimeMessage message = sentMessage();
        assertEquals(FROM, ((InternetAddress) message.getFrom()[0]).getAddress());
        assertArrayEquals(new InternetAddress[] { new InternetAddress(TO) }, message.getAllRecipients());
        assertEquals("Código de verificación del registro en CampusSeguro", message.getSubject());
    }

        /**
     * The body contains the code, its validity in minutes, that the account is only created with the code and the
     * warning for whoever did not make the request
     */
    @Test
    void sendRegistrationCode_body_containsTheCodeTheValidityAndTheIgnoreNotice() throws MessagingException, IOException {
        emailService.sendRegistrationCode(TO, "007391", 20);

        String body = content("text/plain");
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
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> emailService.sendRegistrationCode(TO, "482913", 15));
    }

        /**
     * Both emails have the presentation of CampusSeguro: HTML with the code and the logo attached inside the message
     */
    @Test
    void emails_haveTheBrandedHtmlWithTheInlineLogo() throws MessagingException, IOException {
        emailService.sendRegistrationCode(TO, "482913", 15);

        String html = content("text/html");
        assertNotNull(html);
        assertTrue(html.contains("CampusSeguro"));
        assertTrue(html.contains("482913"));
        assertTrue(html.contains("src=\"cid:campusseguro-logo\""));
        Part logo = parts().stream().filter(p -> {
            try {
                return p.isMimeType("image/png");
            } catch (MessagingException e) {
                return false;
            }
        }).findFirst().orElseThrow();
        assertEquals("<campusseguro-logo>", ((jakarta.mail.internet.MimePart) logo).getContentID());
        assertTrue(Part.INLINE.equalsIgnoreCase(logo.getDisposition()));
    }
}
