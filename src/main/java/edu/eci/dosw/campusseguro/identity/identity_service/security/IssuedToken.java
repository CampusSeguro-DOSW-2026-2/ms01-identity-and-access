package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.time.Instant;

/**
 * JWT ya firmado y su instante de expiracion en segundos exactos
 *
 * @param token JWT compacto firmado
 * @param expiresAt instante de expiracion del token, en segundos exactos
 */
public record IssuedToken(String token, Instant expiresAt) {
}
