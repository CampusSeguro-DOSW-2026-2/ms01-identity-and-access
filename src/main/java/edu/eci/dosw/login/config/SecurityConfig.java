package edu.eci.dosw.login.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de la FASE 1 (MVP).
 *
 * Estrategia: el framework de Spring Security está presente y configurado,
 * pero SIN restricciones de autorización (permitAll) y sin JWT.
 *
 * Esto deja el proyecto listo para la FASE 2:
 *  - Implementar {@code AuthTokenProvider} (JWT) y registrar {@code JwtAuthenticationFilter}.
 *  - Reemplazar {@code anyRequest().permitAll()} por reglas por rol/permiso.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        // TODO Fase 2: http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // TODO Fase 2: .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC).permitAll().anyRequest().authenticated());
        return http.build();
    }
}