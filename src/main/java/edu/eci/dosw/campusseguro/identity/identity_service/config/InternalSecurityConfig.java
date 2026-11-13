package edu.eci.dosw.campusseguro.identity.identity_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import edu.eci.dosw.campusseguro.identity.identity_service.security.ForbiddenAccessDeniedHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.security.InternalServiceAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.security.UnauthenticatedEntryPoint;

/**
 * Seguridad de las rutas internas (RF-MS01-09, RF-MS01-11): cadenas propias que solo atienden esas rutas y se
 * evalúan antes que la de SecurityConfig, que no cambia y sigue atendiendo todo lo demás
 *
 * Las rutas internas se autorizan por el servicio que llama (X-Service-Name), no por los permisos de un
 * usuario: la consulta de usuarios la atienden los servicios de la lista configurada y la de cortes de sesión
 * solo el Gateway
 */
@Configuration
public class InternalSecurityConfig {

    public static final String[] INTERNAL_PATHS = {"/api/v1/users/batch", "/api/v1/users/by-role"};
    public static final String SESSION_CUTS_PATH = "/api/v1/internal/session-cuts";
    public static final String GATEWAY_SERVICE = "gateway";

    /**
     * Cadena de seguridad de las rutas internas
     *
     * @param http configuración de la seguridad HTTP
     * @param allowedServices servicios autorizados separados por coma
     * @return la cadena de filtros de las rutas internas
     * @throws Exception si falla la construcción de la cadena
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http,
            @Value("${internal.allowed-services:ms03,ms05}") String allowedServices) throws Exception {
        http
            .securityMatcher(INTERNAL_PATHS)
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new InternalServiceAuthenticationFilter(allowedServices),
                AnonymousAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new UnauthenticatedEntryPoint())
                .accessDeniedHandler(new ForbiddenAccessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth.anyRequest()
                .hasAuthority(InternalServiceAuthenticationFilter.AUTHORITY));

        return http.build();
    }

    /**
     * Cadena de seguridad de la consulta de cortes de sesión, solo la atiende el servicio gateway y no usa la
     * lista de servicios autorizados de las demás rutas internas
     *
     * @param http configuración de la seguridad HTTP
     * @return la cadena de filtros de la ruta de cortes de sesión
     * @throws Exception si falla la construcción de la cadena
     */
    @Bean
    @Order(2)
    public SecurityFilterChain sessionCutsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(SESSION_CUTS_PATH)
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new InternalServiceAuthenticationFilter(GATEWAY_SERVICE),
                AnonymousAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new UnauthenticatedEntryPoint())
                .accessDeniedHandler(new ForbiddenAccessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth.anyRequest()
                .hasAuthority(InternalServiceAuthenticationFilter.AUTHORITY));

        return http.build();
    }
}
