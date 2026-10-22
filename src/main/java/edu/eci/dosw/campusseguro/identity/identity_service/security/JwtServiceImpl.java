package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;

/**
 * Emite JWT firmados con RS256 (RSASSA-PKCS1-v1_5 con SHA-256, RSA 2048) segun 4_jwt-spec.md.
 *
 * Serializacion JWS compacta (RFC 7515): {@code base64url(header).base64url(payload).base64url(firma)}.
 * La firma usa unicamente el proveedor estandar del JDK ({@code SHA256withRSA}).
 *
 * Header:  {"alg":"RS256","typ":"JWT","kid":"..."}
 * Payload: iss, aud, sub, email, roles (ordenados alfabeticamente), iat, exp (= iat + vigencia), jti.
 */
public class JwtServiceImpl implements JwtService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int MIN_KEY_BITS = 2048;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final long lifetimeSeconds;
    private final Clock clock;

    public JwtServiceImpl(PrivateKey privateKey, String issuer, String audience, String keyId,
            Duration lifetime, Clock clock) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.keyId = requireText(keyId, "keyId");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.toSeconds() <= 0) {
            throw new IllegalArgumentException("La vigencia del JWT debe ser de al menos 1 segundo.");
        }
        this.lifetimeSeconds = lifetime.toSeconds();
        if (!(privateKey instanceof RSAKey rsaKey) || !"RSA".equals(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("La llave de firma del JWT debe ser RSA.");
        }
        if (rsaKey.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalArgumentException("La llave RSA del JWT debe tener al menos 2048 bits.");
        }
    }

    @Override
    public IssuedToken issueToken(UUID userId, String email, List<UserRole> roles) {
        Objects.requireNonNull(userId, "userId");
        requireText(email, "email");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("El JWT debe incluir al menos un rol.");
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(lifetimeSeconds);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", keyId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("aud", audience);
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

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El parámetro '" + name + "' no puede estar vacío.");
        }
        return value;
    }
}
