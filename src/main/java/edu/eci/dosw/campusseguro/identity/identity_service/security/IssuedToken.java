package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.time.Instant;

/**
 * Already signed JWT and its expiration instant in exact seconds
 *
 * @param token signed compact JWT
 * @param expiresAt expiration instant of the token, in exact seconds
 */
public record IssuedToken(String token, Instant expiresAt) {
}
