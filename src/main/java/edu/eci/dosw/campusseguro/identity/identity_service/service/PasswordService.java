package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;

public interface PasswordService {

    MessageResponse requestResetCode(ForgotPasswordRequest request, String correlationId);

    MessageResponse resetPassword(ResetPasswordRequest request, String correlationId);

    MessageResponse changePassword(UUID userId, ChangePasswordRequest request, String correlationId);

}