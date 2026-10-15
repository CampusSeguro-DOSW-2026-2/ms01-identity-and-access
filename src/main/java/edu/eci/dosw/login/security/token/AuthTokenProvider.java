package edu.eci.dosw.login.security.token;

import edu.eci.dosw.login.entity.UserEntity;

/**
 * Puerto de generacion/validacion de tokens de autenticacion.
 *
 * FASE 1: no se usa (el MVP no emite tokens).
 * FASE 2: se implementa con JWT en {@link JwtTokenProvider} y se cablea con AccessService.
 */
public interface AuthTokenProvider {

    AuthToken generateToken(UserEntity user);

    boolean validateToken(String token);

    String extractSubject(String token);
}