package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 403 with the normalized error body (RNF-MS01-04) when the security layer denies access to an authenticated
 * requester
 */
public class ForbiddenAccessDeniedHandler implements AccessDeniedHandler {

    public static final String MESSAGE = ErrorCode.FORBIDDEN.getMessage();

        /**
     * Writes the 403 response with code PROHIBIDO and the correlation id of the request
     *
     * @param request rejected request
     * @param response response where the error is written
     * @param accessDeniedException security reason for the rejection
     * @throws IOException if writing the response fails
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(request, response, ErrorCode.FORBIDDEN, MESSAGE);
    }
}
