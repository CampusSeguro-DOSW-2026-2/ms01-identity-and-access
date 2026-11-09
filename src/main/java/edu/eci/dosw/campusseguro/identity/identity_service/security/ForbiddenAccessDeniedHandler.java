package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 403 con el cuerpo de error normalizado (RNF-MS01-04) cuando la capa de seguridad niega el
 * acceso a un solicitante autenticado
 */
public class ForbiddenAccessDeniedHandler implements AccessDeniedHandler {

    public static final String MESSAGE = ErrorCode.FORBIDDEN.getMessage();

    /**
     * Escribe la respuesta 403 con codigo PROHIBIDO y el correlation id de la peticion
     *
     * @param request peticion rechazada
     * @param response respuesta donde se escribe el error
     * @param accessDeniedException motivo de seguridad del rechazo
     * @throws IOException si falla la escritura de la respuesta
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(request, response, ErrorCode.FORBIDDEN, MESSAGE);
    }
}
