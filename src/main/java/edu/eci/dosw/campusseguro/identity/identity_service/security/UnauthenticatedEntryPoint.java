package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 401 con el cuerpo de error normalizado (RNF-MS01-04) cuando una ruta protegida
 * se llama sin las cabeceras de identidad (RN-MS01-23)
 */
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {

    public static final String CODE = "NO_AUTENTICADO";

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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("codigo", CODE);
        body.put("mensaje", message);
        body.put("errores", List.of());
        body.put("correlation_id", CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER)));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MiniJson.write(body));
    }
}
