package edu.eci.dosw.login.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticación JWT previsto para la FASE 2.
 *
 * NO está activo en Fase 1 (no es un bean y no se registra en la cadena
 * de filtros de {@code SecurityConfig}).
 *
 * TODO Fase 2:
 *  - Leer cabecera Authorization: Bearer.
 *  - Validar firma/expiración con AuthTokenProvider.
 *  - Poblar el SecurityContextHolder.
 *  - Registrar con http.addFilterBefore(...) en SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // FASE 1: no-op. La autenticación real se añade en Fase 2.
        filterChain.doFilter(request, response);
    }
}