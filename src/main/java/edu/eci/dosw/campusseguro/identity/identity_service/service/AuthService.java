package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;

public interface AuthService {

    /**
     * Autentica con correo y contrasena (RF-MS01-02) y emite el JWT.
     *
     * @param request       credenciales (el correo ya llega normalizado, RG-01)
     * @param correlationId valor de X-Correlation-ID; si falta se genera uno para logs y auditoria
     * @return token, tipo, expiracion y datos basicos del usuario
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException
     *         correo inexistente o contrasena incorrecta (misma respuesta, RN-MS01-05)
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException
     *         credenciales correctas pero cuenta INACTIVA (RN-MS01-06)
     */
    LoginResponse login(LoginRequest request, String correlationId);
}
