package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Emision del JWT (RF-MS01-03)
 * MS01 solo emite; la verificacion de la firma la hace el API Gateway con la llave publica.
 */
public interface JwtService {

    /**
     * Emite un JWT RS256 con los claims iss, aud, sub (userId), email, roles, iat, exp y jti.
     * No incluye contrasenas, hashes, nombres, estado ni permisos (RN-MS01-08).
     *
     * @param userId id del usuario
     * @param email  correo del usuario
     * @param roles  roles del usuario
     */
    IssuedToken issueToken(UUID userId, String email, List<UserRole> roles);
}
