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
 * Recovery of a forgotten password and password change of the authenticated user (RF-MS01-08)
 */
public interface PasswordService {

        /**
     * Requests the password recovery code (RF-MS01-08, flow A), the code is generated and sent only if the email
     * corresponds to an active account, but the response is the same in all cases (RN-MS01-20)
     *
     * @param request email of the account to be recovered
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws InvalidRequestException if the email breaks form F-04
     */
    void requestRecoveryCode(ForgotPasswordRequest request, String correlationId);

        /**
     * Resets the password with the code received by email (RF-MS01-08, flow A), the code is single-use and every
     * attempt, successful or failed, is audited
     *
     * @param request email, code and new password with its confirmation
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws InvalidRequestException if the data breaks form F-05 or the new password is equal to the current one
     * @throws InvalidResetCodeException if the code is incorrect, expired or already used
     */
    void resetPassword(ResetPasswordRequest request, String correlationId);

        /**
     * Changes the password of the authenticated user (RF-MS01-08, flow B), every attempt, successful or failed, is
     * audited
     *
     * @param requester identity of whoever changes their password
     * @param request current password, new password and its confirmation
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws InvalidRequestException if the data breaks form F-06, the current password is incorrect or the new one is
     *         equal to the current one
     * @throws UserNotFoundException if the user no longer exists
     */
    void changePassword(RequesterIdentity requester, ChangePasswordRequest request, String correlationId);
}
