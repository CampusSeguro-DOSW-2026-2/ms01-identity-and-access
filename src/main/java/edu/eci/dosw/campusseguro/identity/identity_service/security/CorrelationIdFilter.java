package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guarantees the X-Correlation-ID of each request (RNF-MS01-03), the received one is kept if it is a valid UUID v4 and
 * if it is missing or not valid one is generated, the same value is returned to the client in the response and is
 * available to the controllers, the error handlers and the audit, it is also published in the MDC of the log
 * (RNF-MS01-05) so that each log line of the request starts with it
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

        /**
     * Resolves the correlation id, adds it to the response and continues with the filter chain
     *
     * @param request received request
     * @param response response returned to the client
     * @param filterChain filter chain that follows
     * @throws ServletException if any later filter fails
     * @throws IOException if writing or reading the request fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER));
        request.setAttribute(CorrelationIds.ATTRIBUTE, correlationId);
        response.setHeader(CorrelationIds.HEADER, correlationId);
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            filterChain.doFilter(new CorrelationIdRequest(request, correlationId), response);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

        /**
     * Request that exposes the already resolved correlation id as the value of the X-Correlation-ID header
     */
    private static final class CorrelationIdRequest extends HttpServletRequestWrapper {

        private final String correlationId;

                /**
         * Wraps the original request
         *
         * @param request original request
         * @param correlationId correlation id resolved for this request
         */
        CorrelationIdRequest(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

                /**
         * Returns the value of a header, for X-Correlation-ID the already resolved one
         *
         * @param name name of the header
         * @return the value of the header
         */
        @Override
        public String getHeader(String name) {
            if (CorrelationIds.HEADER.equalsIgnoreCase(name)) {
                return correlationId;
            }
            return super.getHeader(name);
        }

                /**
         * Returns the values of a header, for X-Correlation-ID only the already resolved one
         *
         * @param name name of the header
         * @return the values of the header
         */
        @Override
        public Enumeration<String> getHeaders(String name) {
            if (CorrelationIds.HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(correlationId));
            }
            return super.getHeaders(name);
        }

                /**
         * Returns the names of the headers, including X-Correlation-ID
         *
         * @return the names of the headers of the request
         */
        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(CorrelationIds.HEADER);
            return Collections.enumeration(names);
        }
    }
}
