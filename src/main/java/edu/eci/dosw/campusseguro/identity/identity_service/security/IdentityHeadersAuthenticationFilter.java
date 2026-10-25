package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la peticion con las cabeceras X-User-Id y X-User-Role que inyecta el API Gateway
 * (RN-MS01-23), si las cabeceras faltan o no son validas la peticion queda sin
 * autenticar y las rutas protegidas responden 401
 */
public class IdentityHeadersAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = IdentityHeadersAuthenticationFilter.class.getName() + ".ERROR";

    /**
     * Construye la identidad desde las cabeceras y la deja en el contexto de seguridad
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
        try {
            RequesterIdentity identity = IdentityHeaderParser.parse(
                    request.getHeader(IdentityHeaderParser.USER_ID_HEADER),
                    request.getHeader(IdentityHeaderParser.USER_ROLE_HEADER));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(buildAuthentication(identity));
            SecurityContextHolder.setContext(context);
        } catch (UnauthenticatedException e) {
            request.setAttribute(ERROR_ATTRIBUTE, e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Crea la autenticacion del solicitante con los permisos de sus roles como autoridades
     *
     * @param identity identidad del solicitante
     * @return la autenticacion con la identidad como principal
     */
    private Authentication buildAuthentication(RequesterIdentity identity) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Permission permission : identity.permissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.getCode()));
        }
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities);
    }
}
