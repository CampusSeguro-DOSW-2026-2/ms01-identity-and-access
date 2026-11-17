package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Normalizes the X-Correlation-ID of the request (RNF-MS01-03, Integration Standards, section 8), it is kept if it is a
 * valid UUID v4 and if it is missing or invalid a new one is generated, the correlation never causes the rejection of a
 * request
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";

    public static final String ATTRIBUTE = CorrelationIds.class.getName() + ".VALUE";

    public static final String MDC_KEY = "correlation_id";

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

        /**
     * It is not instantiated
     */
    private CorrelationIds() {
    }

        /**
     * Returns the correlation id used in logs, audit and responses
     *
     * @param received value received in the X-Correlation-ID header, may be null
     * @return the received value without surrounding spaces if it is a valid UUID v4, or a new random UUID v4
     */
    public static String resolve(String received) {
        if (received != null) {
            String trimmed = received.trim();
            if (UUID_V4.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

        /**
     * Returns the correlation id already resolved for the current request, if no filter resolved it before it resolves
     * it from the received header
     *
     * @param request current request
     * @return the correlation id of the request
     */
    public static String current(HttpServletRequest request) {
        Object resolved = request.getAttribute(ATTRIBUTE);
        if (resolved instanceof String value) {
            return value;
        }
        return resolve(request.getHeader(HEADER));
    }
}
