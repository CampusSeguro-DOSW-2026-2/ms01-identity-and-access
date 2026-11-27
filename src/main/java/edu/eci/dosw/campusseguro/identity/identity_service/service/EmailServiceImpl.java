package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sending of MS01 emails by SMTP, the credentials and the server are configured with environment variables
 * (RNF-MS01-06) and the codes are never written to the logs
 *
 * MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD SMTP server and its credentials MAIL_FROM sender of the messages
 *
 * Each message carries the HTML with the presentation of CampusSeguro (logo inside the message) and the same content in
 * plain text for the clients that do not show HTML
 */
@Service
public class EmailServiceImpl implements EmailService {

    public static final String RECOVERY_SUBJECT = "Código de recuperación de contraseña de CampusSeguro";
    public static final String REGISTRATION_SUBJECT = "Código de verificación del registro en CampusSeguro";

    private final JavaMailSender mailSender;
    private final String from;

        /**
     * Creates the service with the SMTP client and the sender
     *
     * @param mailSender SMTP client configured by Spring Boot
     * @param from sender of the messages
     */
    public EmailServiceImpl(JavaMailSender mailSender,
            @Value("${recovery.mail-from:no-reply@campusseguro.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

        /**
     * Sends the password recovery code to the email of the account
     *
     * @param to email to which the code is sent
     * @param code recovery code in clear text, it only exists in memory and in the message
     * @param validMinutes minutes during which the code remains valid
     * @throws MailException if the mail server rejects the message or is not available
     */
    @Override
    public void sendRecoveryCode(String to, String code, int validMinutes) {
        String html = CodeEmailTemplate.html("Recuperación de contraseña", "Restablezca su contraseña",
                "Recibimos una solicitud para restablecer la contraseña de su cuenta de CampusSeguro.",
                "Su código de recuperación", code,
                List.of("El código vence en " + validMinutes + " minutos y solo se puede usar una vez.",
                        "Si no hizo esta solicitud, ignore este mensaje."));
        send(to, RECOVERY_SUBJECT, buildBody(code, validMinutes), html);
    }

        /**
     * Builds the text of the message with the code and its validity
     *
     * @param code recovery code in clear text
     * @param validMinutes minutes during which the code remains valid
     * @return the body of the message
     */
    private String buildBody(String code, int validMinutes) {
        StringBuilder body = new StringBuilder();
        body.append("Hola,\n\n");
        body.append("Recibimos una solicitud para restablecer la contraseña de su cuenta de CampusSeguro.\n\n");
        body.append("Su código de recuperación es: ").append(code).append("\n\n");
        body.append("El código vence en ").append(validMinutes).append(" minutos y solo se puede usar una vez.\n\n");
        body.append("Si no hizo esta solicitud, ignore este mensaje.");
        return body.toString();
    }

        /**
     * Sends the verification code of the registration to the email that is being registered
     *
     * @param to email to which the code is sent
     * @param code verification code in clear text, it only exists in memory and in the message
     * @param validMinutes minutes during which the code remains valid
     * @throws MailException if the mail server rejects the message or is not available
     */
    @Override
    public void sendRegistrationCode(String to, String code, int validMinutes) {
        String html = CodeEmailTemplate.html("Verificación de la cuenta", "Confirme su correo",
                "Recibimos una solicitud para crear una cuenta de CampusSeguro con este correo.",
                "Su código de verificación", code,
                List.of("El código vence en " + validMinutes + " minutos. La cuenta solo se crea cuando ingrese este código.",
                        "Si no hizo esta solicitud, ignore este mensaje: no se creará ninguna cuenta."));
        send(to, REGISTRATION_SUBJECT, buildRegistrationBody(code, validMinutes), html);
    }

        /**
     * Builds the text of the message with the verification code
     *
     * @param code verification code
     * @param validMinutes minutes of validity
     * @return body of the message
     */
    private String buildRegistrationBody(String code, int validMinutes) {
        StringBuilder body = new StringBuilder();
        body.append("Hola,\n\n");
        body.append("Recibimos una solicitud para crear una cuenta de CampusSeguro con este correo.\n\n");
        body.append("Su código de verificación es: ").append(code).append("\n\n");
        body.append("El código vence en ").append(validMinutes).append(" minutos. ");
        body.append("La cuenta solo se crea cuando ingrese este código.\n\n");
        body.append("Si no hizo esta solicitud, ignore este mensaje: no se creará ninguna cuenta.");
        return body.toString();
    }

        /**
     * Sends a message with its plain text, its HTML and the logo attached inline
     *
     * @param to recipient
     * @param subject subject
     * @param text content in plain text
     * @param html content in HTML
     * @throws MailException if the message cannot be built or the mail server rejects it
     */
    private void send(String to, String subject, String text, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            helper.addInline(CodeEmailTemplate.LOGO_CID, "campusseguro-logo.png",
                    new ClassPathResource(CodeEmailTemplate.LOGO_PATH), "image/png");
        } catch (MessagingException e) {
            throw new MailPreparationException("No se pudo preparar el correo", e);
        }
        mailSender.send(message);
    }
}
