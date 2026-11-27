package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeadersAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import jakarta.servlet.FilterChain;

/**
 * RN-MS01-23 and integration standards 2.3: the identity arrives only through the headers X-User-Id, X-User-Role and
 * X-User-Permission, if they are missing or not valid the request is left unauthenticated
 */
class IdentityHeadersAuthenticationFilterTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";

    private IdentityHeadersAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AtomicBoolean chainCalled;

        /**
     * Prepares the filter, a request and a chain that records whether it was invoked
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
     * Clears the security context so as not to affect other tests
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

        /**
     * Runs the filter with a chain that only records that it was invoked
     *
     * @throws Exception if the filter fails
     */
    private void runFilter() throws Exception {
        FilterChain chain = (req, res) -> chainCalled.set(true);
        filter.doFilter(request, response, chain);
    }

        /**
     * With valid headers the request is authenticated with the identity as principal
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_validHeaders_authenticatesWithTheIdentity() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE,BRIGADISTA");
        request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:profile:read,ms05:panel:read_own");

        runFilter();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        RequesterIdentity identity = assertInstanceOf(RequesterIdentity.class, authentication.getPrincipal());
        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(Set.of("ESTUDIANTE", "BRIGADISTA"), identity.roles());
        assertEquals(Set.of("ms01:profile:read", "ms05:panel:read_own"), identity.permissions());
        assertTrue(chainCalled.get());
    }

        /**
     * The authorities are the codes of X-User-Permission, regardless of the name of the roles
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_validHeaders_grantsTheReceivedPermissions() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN");
        request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER,
                String.join(",", TestIdentities.permissionsOf("ADMIN")));

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
     * Without headers it is not authenticated, the reason is stored and the chain continues
     *
     * @throws Exception if the filter fails
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
     * Without X-User-Permission it is not authenticated even if the other two headers are present
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_missingPermissionHeader_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.MISSING_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
        assertTrue(chainCalled.get());
    }

        /**
     * An X-User-Id that is not a UUID does not authenticate
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_invalidUserId_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, "USR-1034");
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN");
        request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:user:roles");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.INVALID_USER_ID_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

        /**
     * A role name with an invalid format does not authenticate
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_invalidRoleFormat_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "SUPER-USUARIO");
        request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:user:roles");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.INVALID_ROLE_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

        /**
     * A lowercase role does not authenticate, the stored role names are uppercase
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_lowercaseRole_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "admin");
        request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:user:roles");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

        /**
     * A permission code with an invalid format does not authenticate
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_invalidPermissionFormat_leavesTheRequestUnauthenticated() throws Exception {
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN");
        request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER, "MS01:USER:ROLES");

        runFilter();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(IdentityHeaderParser.INVALID_PERMISSION_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }
}
