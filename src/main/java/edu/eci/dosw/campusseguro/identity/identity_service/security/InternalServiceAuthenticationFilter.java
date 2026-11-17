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
 * Authenticates the internal routes (RF-MS01-09) with the X-Service-Name header injected by the API Gateway from the
 * service key, it does not use X-User-Id or X-User-Role
 *
 * Without the header the request is left unauthenticated and answers 401 with the reason, a service that is not in the
 * list of authorized ones is authenticated without authorities and answers 403
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String SERVICE_NAME_HEADER = "X-Service-Name";
    public static final String AUTHORITY = "ROLE_INTERNAL_SERVICE";
    public static final String MISSING_MESSAGE = "Falta la cabecera X-Service-Name.";

    private final Set<String> allowedServices;

        /**
     * Creates the filter with the list of authorized services
     *
     * @param allowedServices names of the services separated by comma, spaces, uppercase and empty values are ignored
     */
    public InternalServiceAuthenticationFilter(String allowedServices) {
        this.allowedServices = Arrays.stream(allowedServices == null ? new String[0] : allowedServices.split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

        /**
     * Builds the authentication of the calling service and leaves it in the security context
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
