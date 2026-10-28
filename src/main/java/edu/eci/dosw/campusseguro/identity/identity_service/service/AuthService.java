package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;

/**
 * Autenticación de usuarios (RF-MS01-02) y emisión del token de sesión (RF-MS01-03)
 */
public interface AuthService {

    /**
     * Autentica con correo y contraseña (RF-MS01-02) y emite el JWT (RF-MS01-03)
     *
     * @param request contiene las credenciales; correo y contraseña
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return LoginResponse que contiene token, tipo, expiración y datos básicos del usuario
     * @throws InvalidRequestException si el cuerpo no cumple el formulario F-02
     * @throws InvalidCredentialsException si el correo no existe o la contraseña no coincide
     * @throws InactiveAccountException si las credenciales son correctas pero la cuenta está inactiva
     */
    LoginResponse login(LoginRequest request, String correlationId);
}
