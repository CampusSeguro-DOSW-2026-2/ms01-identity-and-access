package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;

/**
 * The controllers receive the identity of the requester already built from the headers of the API Gateway
 */
class RequesterIdentityArgumentResolverTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";

    private RequesterIdentityArgumentResolver resolver;
    private MockHttpServletRequest request;

        /**
     * Sample method whose parameters are used to query the resolver
     *
     * @param requester parameter of type requester identity
     * @param other parameter of another type
     */
    @SuppressWarnings("unused")
    void sample(RequesterIdentity requester, String other) {
    }

        /**
     * Prepares the resolver and an empty request
     */
    @BeforeEach
    void setUp() {
        resolver = new RequesterIdentityArgumentResolver();
        request = new MockHttpServletRequest();
    }

        /**
     * Gets a parameter of the sample method
     *
     * @param index position of the parameter
     * @return the parameter of the method
     * @throws Exception if the sample method is not found
     */
    private MethodParameter parameter(int index) throws Exception {
        Method method = RequesterIdentityArgumentResolverTest.class.getDeclaredMethod("sample",
                RequesterIdentity.class, String.class);
        return new MethodParameter(method, index);
    }

        /**
     * Only the parameters of type requester identity are resolved
     *
     * @throws Exception if the sample method is not found
     */
    @Test
    void supportsParameter_onlyForTheRequesterIdentityType() throws Exception {
        assertTrue(resolver.supportsParameter(parameter(0)));
        assertFalse(resolver.supportsParameter(parameter(1)));
    }

        /**
     * With the three valid headers the identity is built
     *
     * @throws Exception if the sample method is not found
     */
    @Test
    void resolveArgument_withValidHeaders_buildsTheIdentity() throws Exception {
        request.addHeader("X-User-Id", USER_ID);
        request.addHeader("X-User-Role", "ADMIN,PROFESOR");
        request.addHeader("X-User-Permission", "ms01:user:roles,ms05:incident:report");

        Object resolved = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        RequesterIdentity requester = (RequesterIdentity) resolved;
        assertEquals(UUID.fromString(USER_ID), requester.userId());
        assertEquals(Set.of("ADMIN", "PROFESOR"), requester.roles());
        assertEquals(Set.of("ms01:user:roles", "ms05:incident:report"), requester.permissions());
    }

        /**
     * Without headers the request is not authenticated
     *
     * @throws Exception if the sample method is not found
     */
    @Test
    void resolveArgument_withoutHeaders_isUnauthenticated() throws Exception {
        MethodParameter parameter = parameter(0);
        ServletWebRequest webRequest = new ServletWebRequest(request);

        assertThrows(UnauthenticatedException.class,
                () -> resolver.resolveArgument(parameter, null, webRequest, null));
    }
}
