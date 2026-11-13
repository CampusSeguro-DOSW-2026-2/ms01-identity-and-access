package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.List;
import java.util.UUID;

/**
 * Emision del JWT (RF-MS01-03), MS01 solo emite y la verificacion de la firma la hace el API Gateway con la
 * llave publica
 */
public interface JwtService {

    /**
     * Emite un JWT RS256 con los claims iss, aud, sub (userId), email, roles, permissions, iat, exp y jti, sin
     * incluir contrasenas, hashes, nombres ni estado (RN-MS01-08)
     *
     * @param userId id del usuario
     * @param email correo del usuario
     * @param roles nombres de los roles del usuario
     * @param permissions permisos efectivos del usuario, la union de los de todos sus roles
     * @return el token firmado junto con su fecha de expiracion
     */
    IssuedToken issueToken(UUID userId, String email, List<String> roles, List<String> permissions);
}
