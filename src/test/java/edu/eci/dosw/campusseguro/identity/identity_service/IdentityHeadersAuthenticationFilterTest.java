package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeadersAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import jakarta.servlet.FilterChain;

/**
 * RN-MS01-23 y estandares de integracion 2.3: la identidad llega solo por las cabeceras X-User-Id y
 * X-User-Role, si faltan o no son validas la peticion queda sin autenticar
 */
class IdentityHeadersAuthenticationFilterTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";

    private IdentityHeadersAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AtomicBoolean chainCalled;

    /**
     * Prepara el filtro, una peticion y una cadena que registra si fue invocada
     */
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new IdentityHeadersAuthenticationFilter();
        request = new MockHttpServletRequest("GET", "/api/v1/roles");
        response = new MockHttpServletResponse();
        chainCalled = new AtomicBoolean(false);
    }

    /**
     * Limpia el contexto de seguridad para no afectar a otros tests
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Ejecuta el filtro con una cadena que solo registra que fue invocada
     *
     * @throws Exception si falla el filtro
     */
    private void runFilter() throws Exception {
        FilterChain chain = (req, res) -> chainCalled.set(true);
        filter.doFilter(request, response, chain);
    }

    /**
     * Con cabeceras validas la peticion queda autenticada con la identidad como principal
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_validHeaders_authenticatesWithTheIdentity() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE,BRIGADISTA");

        runFilter();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        RequesterIdentity identity = assertInstanceOf(RequesterIdentity.class, authentication.getPrincipal());
        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE, UserRole.BRIGADISTA), identity.roles());
        assertTrue(chainCalled.get());
    }

    /**
     * Las autoridades son los codigos de los permisos que la matriz concede a sus roles
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_validHeaders_grantsThePermissionsOfTheRoles() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN");

        runFilter();

        Set<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        assertTrue(authorities.contains("ms01:user:roles"));
        assertTrue(authorities.contains("ms01:role:read_all"));
        assertFalse(authorities.contains("ms03:alert:manage"));
    }

    /**
     * Sin cabeceras no se autentica, se guarda el motivo y la cadena continua
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_missingHeaders_leavesTheRequestUnauthenticated() throws Exception {
        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.MISSING_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
        assertTrue(chainCalled.get());
    }

    /**
     * Un X-User-Id que no es un UUID no autentica
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_invalidUserId_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, "USR-1034");
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.INVALID_USER_ID_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

    /**
     * Un rol fuera del catalogo no autentica
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_unknownRole_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "SUPERUSUARIO");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.INVALID_ROLE_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

    /**
     * Un rol en minusculas no autentica, los identificadores de rol son estrictos en mayusculas
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_lowercaseRole_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "admin");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
