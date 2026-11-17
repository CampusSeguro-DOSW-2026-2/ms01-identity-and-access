package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeadersAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.security.InternalServiceAuthenticationFilter;

/**
 * Tests of the filter that authenticates the internal routes by X-Service-Name (RF-MS01-09)
 */
class InternalServiceAuthenticationFilterTest {

    private final InternalServiceAuthenticationFilter filter =
            new InternalServiceAuthenticationFilter(" MS03 , ms05,, ");

        /**
     * Clears the security context between tests
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

        /**
     * Runs the filter with the indicated X-Service-Name value
     *
     * @param serviceName value of the header, null to not send it
     * @return the request after the filter
     * @throws Exception if the filter fails
     */
    private MockHttpServletRequest run(String serviceName) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/batch");
        if (serviceName != null) {
            request.addHeader(InternalServiceAuthenticationFilter.SERVICE_NAME_HEADER, serviceName);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertTrue(chain.getRequest() != null, "la cadena debe continuar");
        return request;
    }

        /**
     * Returns the names of the authorities of the current authentication
     *
     * @return the names of the authorities
     */
    private static List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

        /**
     * Without the header the request is left unauthenticated and with the reason for the 401
     *
     * @param serviceName absent or empty value
     * @throws Exception if the filter fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void filter_missingOrBlankHeader_leavesTheReasonForThe401(String serviceName) throws Exception {
        MockHttpServletRequest request = run(serviceName);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(InternalServiceAuthenticationFilter.MISSING_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

        /**
     * Without the header sent at all the reason for the 401 is also left
     *
     * @throws Exception if the filter fails
     */
    @Test
    void filter_withoutHeader_leavesTheReasonForThe401() throws Exception {
        MockHttpServletRequest request = run(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(InternalServiceAuthenticationFilter.MISSING_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

        /**
     * An authorized service receives the internal authority, regardless of case and spaces
     *
     * @param serviceName authorized service
     * @throws Exception if the filter fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms03", " MS05 "})
    void filter_allowedService_isAuthenticatedWithTheInternalAuthority(String serviceName) throws Exception {
        run(serviceName);

        assertEquals(List.of(InternalServiceAuthenticationFilter.AUTHORITY), authorities());
    }

        /**
     * An unauthorized service is left authenticated without authorities, which produces the 403
     *
     * @throws Exception if the filter fails
     */
    @Test
    void filter_serviceNotAllowed_isAuthenticatedWithoutAuthorities() throws Exception {
        MockHttpServletRequest request = run("ms04");

        assertEquals(List.of(), authorities());
        assertNull(request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }
}
