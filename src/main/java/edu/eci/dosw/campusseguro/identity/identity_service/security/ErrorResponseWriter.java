package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe el ErrorResponse normalizado (RNF-MS01-04) directamente en la respuesta, se usa en los
 * componentes de seguridad que responden antes de que la petición llegue a un controlador
 */
public final class ErrorResponseWriter {

    /**
     * Clase de utilidad, no se instancia
     */
    private ErrorResponseWriter() {
    }

    /**
     * Escribe la respuesta de error con sus cinco campos y el correlation id de la petición
     *
     * @param request petición rechazada, de la que se toma el correlation id
     * @param response respuesta donde se escribe el error
     * @param code código del catálogo de estados y códigos, del que se toma el estado HTTP de la respuesta
     * @param message texto seguro en español
     * @throws IOException si falla la escritura de la respuesta
     */
    public static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
            String message) throws IOException {
        ErrorResponse error = ErrorResponse.of(code.getStatus(), code.getCode(), message,
                CorrelationIds.current(request));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", error.status());
        body.put("codigo", error.codigo());
        body.put("mensaje", error.mensaje());
        body.put("errores", toList(error.errores()));
        body.put("correlation_id", error.correlationId());

        response.setStatus(code.getStatus());
        response.setHeader(CorrelationIds.HEADER, error.correlationId());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MiniJson.write(body));
    }

    /**
     * Convierte los errores por campo en estructuras serializables
     *
     * @param details errores por campo
     * @return lista de mapas con campo y mensaje
     */
    private static List<Map<String, Object>> toList(List<ErrorResponse.ErrorDetail> details) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ErrorResponse.ErrorDetail detail : details) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campo", detail.campo());
            item.put("mensaje", detail.mensaje());
            items.add(item);
        }
        return items;
    }
}
