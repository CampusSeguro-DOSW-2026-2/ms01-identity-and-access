package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica las rutas internas (RF-MS01-09) con la cabecera X-Service-Name que inyecta el API Gateway a
 * partir de la clave de servicio, no usa X-User-Id ni X-User-Role
 *
 * Sin la cabecera la petición queda sin autenticar y responde 401 con el motivo, un servicio que no está en
 * la lista de autorizados queda autenticado sin autoridades y responde 403
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String SERVICE_NAME_HEADER = "X-Service-Name";
    public static final String AUTHORITY = "ROLE_INTERNAL_SERVICE";
    public static final String MISSING_MESSAGE = "Falta la cabecera X-Service-Name.";

    private final Set<String> allowedServices;

    /**
     * Crea el filtro con la lista de servicios autorizados
     *
     * @param allowedServices nombres de los servicios separados por coma, se ignoran los espacios, las
     *        mayúsculas y los valores vacíos
     */
    public InternalServiceAuthenticationFilter(String allowedServices) {
        this.allowedServices = Arrays.stream(allowedServices == null ? new String[0] : allowedServices.split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Construye la autenticación del servicio que llama y la deja en el contexto de seguridad
     *
     * @param request petición recibida
     * @param response respuesta que se devuelve al cliente
     * @param filterChain cadena de filtros que sigue
     * @throws ServletException si falla algún filtro posterior
     * @throws IOException si falla la escritura o lectura de la petición
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(SERVICE_NAME_HEADER);
        if (header == null || header.isBlank()) {
            request.setAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE, MISSING_MESSAGE);
        } else {
            String service = header.trim().toLowerCase(Locale.ROOT);
            List<GrantedAuthority> authorities = allowedServices.contains(service)
                    ? List.of(new SimpleGrantedAuthority(AUTHORITY))
                    : List.of();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(service, null, authorities));
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }
}
