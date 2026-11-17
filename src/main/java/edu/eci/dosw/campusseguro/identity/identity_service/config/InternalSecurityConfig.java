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
 * Security of the internal routes (RF-MS01-09, RF-MS01-11): own chains that serve only those routes and are evaluated
 * before the one of SecurityConfig, which does not change and keeps serving everything else
 *
 * The internal routes are authorized by the calling service (X-Service-Name), not by the permissions of a user: the
 * user query is served by the services of the configured list and the session cuts query only by the Gateway
 */
@Configuration
public class InternalSecurityConfig {

    public static final String[] INTERNAL_PATHS = {"/api/v1/users/batch", "/api/v1/users/by-role"};
    public static final String SESSION_CUTS_PATH = "/api/v1/internal/session-cuts";
    public static final String GATEWAY_SERVICE = "gateway";

        /**
     * Security chain of the internal routes
     *
     * @param http configuration of the HTTP security
     * @param allowedServices authorized services separated by commas
     * @return the filter chain of the internal routes
     * @throws Exception if building the chain fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http,
            @Value("${internal.allowed-services:ms03,ms04,ms05}") String allowedServices) throws Exception {
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
     * Security chain of the session cuts query, it is served only by the gateway service and does not use the list of
     * authorized services of the other internal routes
     *
     * @param http configuration of the HTTP security
     * @return the filter chain of the session cuts route
     * @throws Exception if building the chain fails
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
