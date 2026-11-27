package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResendRegistrationCodeRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.VerifyRegistrationRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidVerificationCodeException;

/**
 * Registration of institutional users with verification of the email (RF-MS01-01): the user is only created when the
 * code sent to the email is confirmed
 */
public interface RegistrationService {

        /**
     * Receives the registration form, keeps it as a pending registration and sends a verification code to the email
     *
     * @param request data of the registration form
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the registration token that only whoever made this registration receives, it is required to confirm it
     * @throws InvalidRequestException if the data breaks form F-01
     * @throws EmailAlreadyExistsException if the email already belongs to a user
     */
    String requestRegistration(UserCreateRequest request, String correlationId);

        /**
     * Confirms the code sent to the email and creates the user of the pending registration
     *
     * @param request email and code
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the created user with its id, email, roles and status
     * @throws InvalidRequestException if the data breaks form F-14
     * @throws InvalidVerificationCodeException if there is no pending registration of that token or the code is
     *         incorrect or expired
     * @throws EmailAlreadyExistsException if the email became a user while the registration was pending
     */
    UserResponse verify(VerifyRegistrationRequest request, String correlationId);

        /**
     * Sends a new verification code to an email with a pending registration, the response is the same whether or not
     * the code is sent
     *
     * @param request email of the pending registration
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs
     * @throws InvalidRequestException if the email breaks form F-14
     */
    void resendCode(ResendRegistrationCodeRequest request, String correlationId);
}
