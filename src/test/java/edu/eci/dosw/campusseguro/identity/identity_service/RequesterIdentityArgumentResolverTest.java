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

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;

/**
 * Los controladores reciben la identidad del solicitante ya construida a partir de las cabeceras del
 * API Gateway
 */
class RequesterIdentityArgumentResolverTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";

    private RequesterIdentityArgumentResolver resolver;
    private MockHttpServletRequest request;

    /**
     * Método de ejemplo cuyos parámetros se usan para consultar al resolvedor
     *
     * @param requester parámetro de tipo identidad del solicitante
     * @param other parámetro de otro tipo
     */
    @SuppressWarnings("unused")
    void sample(RequesterIdentity requester, String other) {
    }

    /**
     * Prepara el resolvedor y una petición vacía
     */
    @BeforeEach
    void setUp() {
        resolver = new RequesterIdentityArgumentResolver();
        request = new MockHttpServletRequest();
    }

    /**
     * Obtiene un parámetro del método de ejemplo
     *
     * @param index posición del parámetro
     * @return el parámetro del método
     * @throws Exception si no se encuentra el método de ejemplo
     */
    private MethodParameter parameter(int index) throws Exception {
        Method method = RequesterIdentityArgumentResolverTest.class.getDeclaredMethod("sample",
                RequesterIdentity.class, String.class);
        return new MethodParameter(method, index);
    }

    /**
     * Solo se resuelven los parámetros de tipo identidad del solicitante
     *
     * @throws Exception si no se encuentra el método de ejemplo
     */
    @Test
    void supportsParameter_onlyForTheRequesterIdentityType() throws Exception {
        assertTrue(resolver.supportsParameter(parameter(0)));
        assertFalse(resolver.supportsParameter(parameter(1)));
    }

    /**
     * Con las dos cabeceras válidas se construye la identidad
     *
     * @throws Exception si no se encuentra el método de ejemplo
     */
    @Test
    void resolveArgument_withValidHeaders_buildsTheIdentity() throws Exception {
        request.addHeader("X-User-Id", USER_ID);
        request.addHeader("X-User-Role", "ADMIN,PROFESOR");

        Object resolved = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        RequesterIdentity requester = (RequesterIdentity) resolved;
        assertEquals(UUID.fromString(USER_ID), requester.userId());
        assertEquals(Set.of(UserRole.ADMIN, UserRole.PROFESOR), requester.roles());
    }

    /**
     * Sin cabeceras la petición no está autenticada
     *
     * @throws Exception si no se encuentra el método de ejemplo
     */
    @Test
    void resolveArgument_withoutHeaders_isUnauthenticated() throws Exception {
        MethodParameter parameter = parameter(0);
        ServletWebRequest webRequest = new ServletWebRequest(request);

        assertThrows(UnauthenticatedException.class,
                () -> resolver.resolveArgument(parameter, null, webRequest, null));
    }
}
