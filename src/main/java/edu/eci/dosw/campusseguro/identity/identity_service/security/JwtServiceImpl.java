package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;

/**
 * Emite JWT firmados con RS256 (RSASSA-PKCS1-v1_5 con SHA-256, RSA 2048)
 *
 * Serializacion JWS compacta (RFC 7515): Contiene header, payload y firma
 * La firma usa unicamente el proveedor estandar del JDK
 *
 * Header:  {"alg":"RS256","typ":"JWT","kid":"..."}
 * Payload: iss, aud, sub, email, roles (ordenados alfabeticamente), iat, exp (= iat + vigencia), jti
 */
public class JwtServiceImpl implements JwtService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int MIN_KEY_BITS = 2048;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;
    private final String keyId;
    private final Clock clock;

    /**
     * Crea el emisor de tokens y valida la llave según la especificación del JWT, el emisor, la audiencia y
     * la vigencia son los valores globales de los Estándares de Integración
     *
     * @param privateKey llave privada RSA con la que se firman los tokens
     * @param keyId valor del campo kid del header
     * @param clock reloj con el que se calculan iat y exp
     * @throws IllegalArgumentException si el identificador de la llave está vacío o la llave no es RSA de al
     *         menos 2048 bits
     */
    public JwtServiceImpl(PrivateKey privateKey, String keyId, Clock clock) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.keyId = requireText(keyId, "keyId");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!(privateKey instanceof RSAKey rsaKey) || !"RSA".equals(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("La llave de firma del JWT debe ser RSA.");
        }
        if (rsaKey.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalArgumentException("La llave RSA del JWT debe tener al menos 2048 bits.");
        }
    }

    /**
     * Emite un JWT RS256 con los claims iss, aud, sub (userId), email, roles, iat, exp y jti, sin incluir
     * contrasenas, hashes, nombres, estado ni permisos (RN-MS01-08)
     *
     * @param userId id del usuario
     * @param email correo del usuario
     * @param roles roles del usuario
     * @return el token firmado junto con su fecha de expiración
     * @throws IllegalArgumentException si el correo está vacío o el usuario no tiene ningún rol
     */
    @Override
    public IssuedToken issueToken(UUID userId, String email, List<UserRole> roles) {
        Objects.requireNonNull(userId, "userId");
        requireText(email, "email");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("El JWT debe incluir al menos un rol.");
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(GlobalValues.JWT_LIFETIME_SECONDS);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", keyId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", GlobalValues.JWT_ISSUER);
        payload.put("aud", GlobalValues.JWT_AUDIENCE);
        payload.put("sub", userId.toString());
        payload.put("email", email);
        payload.put("roles", roles.stream().map(UserRole::name).sorted().toList());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("jti", UUID.randomUUID().toString());

        String signingInput = encode(MiniJson.write(header)) + "." + encode(MiniJson.write(payload));
        String signature = BASE64_URL.encodeToString(sign(signingInput));
        return new IssuedToken(signingInput + "." + signature, expiresAt);
    }

    /**
     * Genera la firma digital del token
     *
     * @param signingInput header y payload codificado en BASE64_URL
     * @return la firma digital
     */
    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No fue posible firmar el JWT.", e);
        }
    }

    /**
     * Codifica en formato BASE64_URL el String recibido
     *
     * @param json cadena recibida y que se debe de codificar
     * @return devuelve la codificacion en BASE64
     */
    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifica que el valor de un campo sea valido (es requerido), revisando que no sea nulo ni este
     * lleno de espacios
     *
     * @param value valor de un campo
     * @param name nombre del campo
     * @return el valor recibido solo cuando se considera valido
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El parámetro '" + name + "' no puede estar vacío.");
        }
        return value;
    }
}
