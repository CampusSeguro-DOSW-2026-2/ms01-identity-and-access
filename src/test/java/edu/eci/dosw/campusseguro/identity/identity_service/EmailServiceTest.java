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
 * Pruebas del envío del código de recuperación por correo (RF-MS01-08): destinatario, remitente, asunto y
 * contenido del mensaje, y propagación del fallo del servidor de correo
 */
class EmailServiceTest {

    private static final String FROM = "no-reply@escuelaing.edu.co";
    private static final String TO = "maria.perez@escuelaing.edu.co";

    private JavaMailSender mailSender;
    private EmailServiceImpl emailService;

    /**
     * Prepara el servicio con un cliente SMTP simulado
     */
    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailServiceImpl(mailSender, FROM);
    }

    /**
     * Captura el mensaje que se entrega al cliente SMTP
     *
     * @return el mensaje enviado
     */
    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    /**
     * El mensaje se envía al correo de la cuenta, desde el remitente configurado y con el asunto de la
     * recuperación
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
     * El cuerpo contiene el código, su vigencia en minutos y la advertencia para quien no hizo la solicitud
     */
    @Test
    void sendRecoveryCode_body_containsTheCodeTheValidityAndTheIgnoreNotice() {
        emailService.sendRecoveryCode(TO, "007391", 15);

        String body = sentMessage().getText();
        assertNotNull(body);
        assertTrue(body.contains("007391"));
        assertTrue(body.contains("15 minutos"));
        assertTrue(body.contains("solo se puede usar una vez"));
        assertTrue(body.contains("Si no hiciste esta solicitud"));
    }

    /**
     * La vigencia que aparece en el mensaje es la recibida
     */
    @Test
    void sendRecoveryCode_customValidity_isShownInTheBody() {
        emailService.sendRecoveryCode(TO, "123456", 30);

        assertTrue(sentMessage().getText().contains("30 minutos"));
    }

    /**
     * Si el servidor de correo falla el error se propaga a quien llama, que decide qué hacer
     */
    @Test
    void sendRecoveryCode_whenTheMailServerFails_propagatesTheMailException() {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> emailService.sendRecoveryCode(TO, "482913", 15));
    }
}
