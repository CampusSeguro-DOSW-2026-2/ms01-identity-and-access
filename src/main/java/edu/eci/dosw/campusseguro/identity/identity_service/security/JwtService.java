package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.List;
import java.util.UUID;

/**
 * Issuance of the JWT (RF-MS01-03), MS01 only issues and the verification of the signature is done by the API Gateway
 * with the public key
 */
public interface JwtService {

        /**
     * Issues an RS256 JWT with the claims iss, aud, sub (userId), email, roles, permissions, iat, exp and jti, without
     * including passwords, hashes, names or status (RN-MS01-08)
     *
     * @param userId id of the user
     * @param email email of the user
     * @param roles names of the roles of the user
     * @param permissions effective permissions of the user, the union of those of all their roles
     * @return the signed token together with its expiration date
     */
    IssuedToken issueToken(UUID userId, String email, List<String> roles, List<String> permissions);
}
