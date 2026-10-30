package edu.eci.dosw.campusseguro.identity.identity_service.service;

import org.springframework.mail.MailException;

/**
 * Envío de correos de MS01 con el servidor de correo institucional configurado (RF-MS01-08)
 */
public interface EmailService {

    /**
     * Envía el código de recuperación de contraseña al correo de la cuenta
     *
     * @param to correo al que se envía el código
     * @param code código de recuperación en claro, solo existe en memoria y en el mensaje
     * @param validMinutes minutos que el código permanece vigente
     * @throws MailException si el servidor de correo rechaza el mensaje o no está disponible
     */
    void sendRecoveryCode(String to, String code, int validMinutes);
}
