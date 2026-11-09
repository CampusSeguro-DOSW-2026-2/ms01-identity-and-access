package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Recuperación de la contraseña olvidada y cambio de contraseña del usuario autenticado (RF-MS01-08)
 */
public interface PasswordService {

    /**
     * Solicita el código de recuperación de contraseña (RF-MS01-08, flujo A), el código se genera y se envía
     * solo si el correo corresponde a una cuenta activa, pero la respuesta es la misma en todos los casos
     * (RN-MS01-20)
     *
     * @param request correo de la cuenta que se quiere recuperar
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws InvalidRequestException si el correo incumple el formulario F-04
     */
    void requestRecoveryCode(ForgotPasswordRequest request, String correlationId);

    /**
     * Restablece la contraseña con el código recibido por correo (RF-MS01-08, flujo A), el código es de un
     * solo uso y cada intento, exitoso o fallido, queda auditado
     *
     * @param request correo, código y nueva contraseña con su confirmación
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws InvalidRequestException si los datos incumplen el formulario F-05 o la nueva contraseña es igual
     *         a la actual
     * @throws InvalidResetCodeException si el código es incorrecto, venció o ya se usó
     */
    void resetPassword(ResetPasswordRequest request, String correlationId);

    /**
     * Cambia la contraseña del usuario autenticado (RF-MS01-08, flujo B), cada intento, exitoso o fallido,
     * queda auditado
     *
     * @param requester identidad de quien cambia su contraseña
     * @param request contraseña actual, nueva contraseña y su confirmación
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws InvalidRequestException si los datos incumplen el formulario F-06, la contraseña actual es
     *         incorrecta o la nueva es igual a la actual
     * @throws UserNotFoundException si el usuario ya no existe
     */
    void changePassword(RequesterIdentity requester, ChangePasswordRequest request, String correlationId);
}
