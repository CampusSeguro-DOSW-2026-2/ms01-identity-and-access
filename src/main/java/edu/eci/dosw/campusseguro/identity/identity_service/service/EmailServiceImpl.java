package edu.eci.dosw.campusseguro.identity.identity_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos de MS01 por SMTP, las credenciales y el servidor se configuran con variables de entorno
 * (RNF-MS01-06) y el código de recuperación nunca se escribe en los logs
 *
 * MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD  servidor SMTP y sus credenciales
 * MAIL_FROM                                           remitente de los mensajes
 */
@Service
public class EmailServiceImpl implements EmailService {

    public static final String RECOVERY_SUBJECT = "Código de recuperación de contraseña de CampusSeguro";

    private final JavaMailSender mailSender;
    private final String from;

    /**
     * Crea el servicio con el cliente SMTP y el remitente
     *
     * @param mailSender cliente SMTP configurado por Spring Boot
     * @param from remitente de los mensajes
     */
    public EmailServiceImpl(JavaMailSender mailSender,
            @Value("${recovery.mail-from:no-reply@campusseguro.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * Envía el código de recuperación de contraseña al correo de la cuenta
     *
     * @param to correo al que se envía el código
     * @param code código de recuperación en claro, solo existe en memoria y en el mensaje
     * @param validMinutes minutos que el código permanece vigente
     * @throws MailException si el servidor de correo rechaza el mensaje o no está disponible
     */
    @Override
    public void sendRecoveryCode(String to, String code, int validMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(RECOVERY_SUBJECT);
        message.setText(buildBody(code, validMinutes));
        mailSender.send(message);
    }

    /**
     * Construye el texto del mensaje con el código y su vigencia
     *
     * @param code código de recuperación en claro
     * @param validMinutes minutos que el código permanece vigente
     * @return el cuerpo del mensaje
     */
    private String buildBody(String code, int validMinutes) {
        StringBuilder body = new StringBuilder();
        body.append("Hola,\n\n");
        body.append("Recibimos una solicitud para restablecer la contraseña de tu cuenta de CampusSeguro.\n\n");
        body.append("Tu código de recuperación es: ").append(code).append("\n\n");
        body.append("El código vence en ").append(validMinutes).append(" minutos y solo se puede usar una vez.\n\n");
        body.append("Si no hiciste esta solicitud, puedes ignorar este mensaje.");
        return body.toString();
    }
}
