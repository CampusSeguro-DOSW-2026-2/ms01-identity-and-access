package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;

public interface PasswordService {

    MessageResponse requestResetCode(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);

}