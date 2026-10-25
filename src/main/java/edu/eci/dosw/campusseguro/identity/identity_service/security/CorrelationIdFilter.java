package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 * Garantiza el X-Correlation-ID de cada peticion (RNF-MS01-03), se conserva el recibido si es valido y
 * si falta se genera uno, el mismo valor se devuelve al cliente en la respuesta y queda disponible
 * para los controladores, los manejadores de error y la auditoria
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Resuelve el correlation id, lo agrega a la respuesta y continua con la cadena de filtros
     *
     * @param request peticion recibida
     * @param response respuesta que se devuelve al cliente
     * @param filterChain cadena de filtros que sigue
     * @throws ServletException si falla algun filtro posterior
     * @throws IOException si falla la escritura o lectura de la peticion
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER));
        response.setHeader(CorrelationIds.HEADER, correlationId);
        filterChain.doFilter(new CorrelationIdRequest(request, correlationId), response);
    }

    /**
     * Peticion que expone el correlation id ya resuelto como valor de la cabecera X-Correlation-ID
     */
    private static final class CorrelationIdRequest extends HttpServletRequestWrapper {

        private final String correlationId;

        /**
         * Envuelve la peticion original
         *
         * @param request peticion original
         * @param correlationId correlation id resuelto para esta peticion
         */
        CorrelationIdRequest(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        /**
         * Devuelve el valor de una cabecera, para X-Correlation-ID el ya resuelto
         *
         * @param name nombre de la cabecera
         * @return el valor de la cabecera
         */
        @Override
        public String getHeader(String name) {
            if (CorrelationIds.HEADER.equalsIgnoreCase(name)) {
                return correlationId;
            }
            return super.getHeader(name);
        }

        /**
         * Devuelve los valores de una cabecera, para X-Correlation-ID solo el ya resuelto
         *
         * @param name nombre de la cabecera
         * @return los valores de la cabecera
         */
        @Override
        public Enumeration<String> getHeaders(String name) {
            if (CorrelationIds.HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(correlationId));
            }
            return super.getHeaders(name);
        }

        /**
         * Devuelve los nombres de las cabeceras, incluida X-Correlation-ID
         *
         * @return los nombres de las cabeceras de la peticion
         */
        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(CorrelationIds.HEADER);
            return Collections.enumeration(names);
        }
    }
}
