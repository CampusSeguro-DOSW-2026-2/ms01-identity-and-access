package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;

public interface AuthService {

    /**
     * Autentica con correo y contrasena (RF-MS01-02) y emite el JWT (RF-MS01-03)
     *
     * @param request contiene las credenciales; correo y contraseña
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoria
     * @return LoginResponse que contiene token, tipo, expiracion y datos basicos del usuario
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException
     */
    LoginResponse login(LoginRequest request, String correlationId);
}
