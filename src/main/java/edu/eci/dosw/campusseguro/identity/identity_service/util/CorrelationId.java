package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resuelve el X-Correlation-ID de una peticion (Estandares, seccion 8): se conserva el valor recibido
 * si es seguro y, si falta o no es valido, se genera un UUID. El valor queda guardado en la
 * peticion para que el controller, el servicio, la auditoria y los errores usen siempre el mismo.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-ID";

    private static final String ATTRIBUTE = CorrelationId.class.getName() + ".value";
    private static final Pattern SAFE = Pattern.compile("^[\\x21-\\x7E]{1,100}$");

    private CorrelationId() {
    }

    /** Devuelve el valor recibido (recortado) si es seguro; si no, un UUID nuevo. */
    public static String resolve(String raw) {
        if (raw != null) {
            String trimmed = raw.trim();
            if (SAFE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    /** Resuelve el id de la peticion una sola vez y lo reutiliza durante toda su vida. */
    public static String from(HttpServletRequest request) {
        Object cached = request.getAttribute(ATTRIBUTE);
        if (cached instanceof String value) {
            return value;
        }
        String value = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, value);
        return value;
    }
}
