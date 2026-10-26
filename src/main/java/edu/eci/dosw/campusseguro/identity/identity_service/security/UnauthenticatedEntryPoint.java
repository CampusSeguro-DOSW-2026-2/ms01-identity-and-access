package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 401 con el cuerpo de error normalizado (RNF-MS01-04) cuando una ruta protegida
 * se llama sin las cabeceras de identidad (RN-MS01-23)
 */
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {

    public static final String CODE = ErrorCode.UNAUTHENTICATED.getCode();

    /**
     * Escribe la respuesta 401 con codigo NO_AUTENTICADO y el correlation id de la peticion
     *
     * @param request peticion rechazada
     * @param response respuesta donde se escribe el error
     * @param authException motivo de seguridad del rechazo
     * @throws IOException si falla la escritura de la respuesta
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
