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
 * Pruebas del filtro que autentica las rutas internas por X-Service-Name (RF-MS01-09)
 */
class InternalServiceAuthenticationFilterTest {

    private final InternalServiceAuthenticationFilter filter =
            new InternalServiceAuthenticationFilter(" MS03 , ms05,, ");

    /**
     * Limpia el contexto de seguridad entre pruebas
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Ejecuta el filtro con el valor de X-Service-Name indicado
     *
     * @param serviceName valor de la cabecera, null para no enviarla
     * @return la petición después del filtro
     * @throws Exception si falla el filtro
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
     * Devuelve los nombres de las autoridades de la autenticación actual
     *
     * @return los nombres de las autoridades
     */
    private static List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    /**
     * Sin la cabecera la petición queda sin autenticar y con el motivo para el 401
     *
     * @param serviceName valor ausente o vacío
     * @throws Exception si falla el filtro
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
     * Sin la cabecera enviada en absoluto también queda el motivo para el 401
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void filter_withoutHeader_leavesTheReasonForThe401() throws Exception {
        MockHttpServletRequest request = run(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(InternalServiceAuthenticationFilter.MISSING_MESSAGE,
                request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }

    /**
     * Un servicio autorizado recibe la autoridad interna, sin distinguir mayúsculas ni espacios
     *
     * @param serviceName servicio autorizado
     * @throws Exception si falla el filtro
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms03", " MS05 "})
    void filter_allowedService_isAuthenticatedWithTheInternalAuthority(String serviceName) throws Exception {
        run(serviceName);

        assertEquals(List.of(InternalServiceAuthenticationFilter.AUTHORITY), authorities());
    }

    /**
     * Un servicio no autorizado queda autenticado sin autoridades, lo que produce el 403
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void filter_serviceNotAllowed_isAuthenticatedWithoutAuthorities() throws Exception {
        MockHttpServletRequest request = run("ms04");

        assertEquals(List.of(), authorities());
        assertNull(request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE));
    }
}
