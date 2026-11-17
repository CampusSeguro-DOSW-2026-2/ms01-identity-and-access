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
 * Writes the normalized ErrorResponse (RNF-MS01-04) directly into the response, it is used in the security components
 * that answer before the request reaches a controller
 */
public final class ErrorResponseWriter {

        /**
     * Utility class, it is not instantiated
     */
    private ErrorResponseWriter() {
    }

        /**
     * Writes the error response with its five fields and the correlation id of the request
     *
     * @param request rejected request, from which the correlation id is taken
     * @param response response where the error is written
     * @param code code of the catalog of statuses and codes, from which the HTTP status of the response is taken
     * @param message safe text in Spanish
     * @throws IOException if writing the response fails
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
     * Converts the errors by field into serializable structures
     *
     * @param details errors by field
     * @return list of maps with field and message
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
