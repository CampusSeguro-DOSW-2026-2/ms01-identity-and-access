package edu.eci.dosw.campusseguro.identity.identity_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import edu.eci.dosw.campusseguro.identity.identity_service.security.ForbiddenAccessDeniedHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeadersAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.security.UnauthenticatedEntryPoint;

/**
 * Security configuration of MS01 (RN-MS01-23, RNF-MS01-03, RNF-MS01-09), MS01 never receives or validates the JWT: it
 * trusts the identity headers injected by the API Gateway and does not configure CORS
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        /**
     * HTTP filter chain, only the exact public routes of the integration standard are public and everything else
     * requires the identity of the headers X-User-Id, X-User-Role and X-User-Permission injected by the API Gateway
     * (RN-MS01-23)
     *
     * If it is missing, 401 is answered with the ErrorResponse body
     *
     * The authorization (403) is decided by each service with the permissions of X-User-Permission
     *
     * @param http builder of the HTTP security configuration
     * @param swaggerEnabled tells whether the OpenAPI documentation is enabled (only in local development)
     * @return the configured filter chain
     * @throws Exception if the configuration cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerEnabled) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new IdentityHeadersAuthenticationFilter(), AnonymousAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new UnauthenticatedEntryPoint())
                .accessDeniedHandler(new ForbiddenAccessDeniedHandler()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/password/forgot").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/password/reset").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                auth.requestMatchers("/error").permitAll();
                if (swaggerEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                auth.anyRequest().authenticated();
            });

        return http.build();
    }

    /**
     * Encoder with which the passwords are stored (RN-MS01-04)
     *
     * @return BCrypt encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
