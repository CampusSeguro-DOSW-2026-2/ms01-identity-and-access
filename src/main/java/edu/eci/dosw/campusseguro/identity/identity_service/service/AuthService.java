package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;

/**
 * User authentication (RF-MS01-02) and issuance of the session token (RF-MS01-03)
 */
public interface AuthService {

        /**
     * Authenticates with email and password (RF-MS01-02) and issues the JWT (RF-MS01-03)
     *
     * @param request contains the credentials; email and password
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return LoginResponse containing token, type, expiration and basic data of the user
     * @throws InvalidRequestException if the body does not meet form F-02
     * @throws InvalidCredentialsException if the email does not exist or the password does not match
     * @throws InactiveAccountException if the credentials are correct but the account is inactive
     */
    LoginResponse login(LoginRequest request, String correlationId);
}
