package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.time.Instant;

/**
 * JWT ya firmado y su instante de expiracion (claim {@code exp}, en segundos exactos).
 */
public record IssuedToken(String token, Instant expiresAt) {
}
