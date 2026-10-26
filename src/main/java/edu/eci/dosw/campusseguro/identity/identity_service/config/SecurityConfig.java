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
 * Configuración de seguridad de MS01 (RN-MS01-23, RNF-MS01-03, RNF-MS01-09), MS01 nunca recibe ni valida
 * el JWT: confía en las cabeceras de identidad que inyecta el API Gateway y no configura CORS
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Cadena de filtros HTTP, solo las rutas publicas exactas del estandar de integracion son
     * publicas y el resto exige la identidad de las cabeceras X-User-Id y X-User-Role
     * que inyecta el API Gateway (RN-MS01-23)
     *
     * Si falta se responde 401 con el cuerpo ErrorResponse
     *
     * La autorizacion (403) la decide cada servicio con la matriz Rol-Permiso
     *
     * @param http constructor de la configuracion de seguridad HTTP
     * @param swaggerEnabled indica si la documentacion OpenAPI esta habilitada (solo en desarrollo local)
     * @return la cadena de filtros configurada
     * @throws Exception si la configuracion no se puede construir
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
     * Codificador con el que se guardan las contrasenas (RN-MS01-04)
     *
     * @return codificador BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
