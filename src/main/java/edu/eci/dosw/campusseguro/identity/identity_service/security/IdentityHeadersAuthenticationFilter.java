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

import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates the request with the headers X-User-Id, X-User-Role and X-User-Permission injected by the API Gateway
 * (RN-MS01-23), if the headers are missing or not valid the request is left unauthenticated and the protected routes
 * answer 401
 */
public class IdentityHeadersAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = IdentityHeadersAuthenticationFilter.class.getName() + ".ERROR";

        /**
     * Builds the identity from the headers and leaves it in the security context
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
        try {
            RequesterIdentity identity = IdentityHeaderParser.parse(
                    request.getHeader(IdentityHeaderParser.USER_ID_HEADER),
                    request.getHeader(IdentityHeaderParser.USER_ROLE_HEADER),
                    request.getHeader(IdentityHeaderParser.USER_PERMISSION_HEADER));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(buildAuthentication(identity));
            SecurityContextHolder.setContext(context);
        } catch (UnauthenticatedException e) {
            request.setAttribute(ERROR_ATTRIBUTE, e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

        /**
     * Creates the authentication of the requester with the permissions of their roles as authorities
     *
     * @param identity identity of the requester
     * @return the authentication with the identity as principal
     */
    private Authentication buildAuthentication(RequesterIdentity identity) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : identity.permissions()) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities);
    }
}
