package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 401 with the normalized error body (RNF-MS01-04) when a protected route is called without the identity
 * headers (RN-MS01-23)
 */
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {

    public static final String CODE = ErrorCode.UNAUTHENTICATED.getCode();

        /**
     * Writes the 401 response with code NO_AUTENTICADO and the correlation id of the request
     *
     * @param request rejected request
     * @param response response where the error is written
     * @param authException security reason for the rejection
     * @throws IOException if writing the response fails
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = IdentityHeaderParser.MISSING_MESSAGE;
        Object reason = request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE);
        if (reason instanceof String text) {
            message = text;
        }
        ErrorResponseWriter.write(request, response, ErrorCode.UNAUTHENTICATED, message);
    }
}
