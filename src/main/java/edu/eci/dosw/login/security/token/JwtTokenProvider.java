package edu.eci.dosw.login.security.token;

import edu.eci.dosw.login.entity.UserEntity;

/**
 * Implementacion JWT prevista para la FASE 2.
 *
 * NO esta activa en la Fase 1 (no es un bean).
 *
 * TODO Fase 2:
 *  - Anadir dependencia jjwt (api/impl/jackson).
 *  - Leer el secreto desde la variable de entorno JWT_SECRET (prohibido hardcodear).
 *  - Firmar con HS256 (o RS256 si se define) e incluir claims: sub, rol, iat, exp, jti.
 */
public class JwtTokenProvider implements AuthTokenProvider {

    @Override
    public AuthToken generateToken(UserEntity user) {
        throw new UnsupportedOperationException("JWT no implementado en la Fase 1 (ver TODO Fase 2)");
    }

    @Override
    public boolean validateToken(String token) {
        throw new UnsupportedOperationException("JWT no implementado en la Fase 1 (ver TODO Fase 2)");
    }

    @Override
    public String extractSubject(String token) {
        throw new UnsupportedOperationException("JWT no implementado en la Fase 1 (ver TODO Fase 2)");
    }
}