package edu.eci.dosw.campusseguro.identity.identity_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String sender;

    public EmailServiceImpl(JavaMailSender mailSender,
            @Value("${campusseguro.recuperacion.remitente}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Override
    public void sendResetCode(String to, String code, int validMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(to);
        message.setSubject("Código de recuperación de contraseña de CampusSeguro");
        message.setText(
            "Hola,\n\n"
            + "Recibimos una solicitud para restablecer la contraseña de tu cuenta de CampusSeguro.\n\n"
            + "Tu código de recuperación es: " + code + "\n\n"
            + "El código vence en " + validMinutes + " minutos y solo se puede usar una vez.\n\n"
            + "Si no hiciste esta solicitud, puedes ignorar este mensaje.");
        mailSender.send(message);
    }
}