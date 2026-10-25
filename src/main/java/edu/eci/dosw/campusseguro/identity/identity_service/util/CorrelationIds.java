package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Normaliza el X-Correlation-ID de la peticion (RNF-MS01-03), se conserva si es un valor imprimible de
 * hasta 100 caracteres y si falta o no es valido se genera uno nuevo
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";

    private static final Pattern SAFE_VALUE = Pattern.compile("^[\\x21-\\x7E]{1,100}$");

    /**
     * Clase de utilidad, no se instancia
     */
    private CorrelationIds() {
    }

    /**
     * Devuelve el correlation id que se usa en logs, auditoria y respuestas
     *
     * @param received valor recibido en la cabecera X-Correlation-ID
     * @return el valor recibido sin espacios laterales si es valido, o un UUID aleatorio nuevo
     */
    public static String resolve(String received) {
        if (received != null) {
            String trimmed = received.trim();
            if (SAFE_VALUE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}
