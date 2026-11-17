package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;

/**
 * Issues JWTs signed with RS256 (RSASSA-PKCS1-v1_5 with SHA-256, RSA 2048)
 *
 * Compact JWS serialization (RFC 7515): It contains header, payload and signature The signature uses only the standard
 * JDK provider
 *
 * Header: {"alg":"RS256","typ":"JWT","kid":"..."}
 * Payload: iss, aud, sub, email, roles and permissions (sorted alphabetically), iat, exp (= iat + validity), jti
 */
public class JwtServiceImpl implements JwtService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int MIN_KEY_BITS = 2048;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final PrivateKey privateKey;
    private final String keyId;
    private final DatabaseTimeService timeService;

        /**
     * Creates the token issuer and validates the key according to the JWT specification, the issuer, the audience and
     * the validity are the global values of the Integration Standards
     *
     * @param privateKey RSA private key with which the tokens are signed
     * @param keyId value of the kid field of the header
     * @param timeService single clock of the database with which iat and exp are calculated
     * @throws IllegalArgumentException if the key identifier is empty or the key is not RSA of at least 2048 bits
     */
    public JwtServiceImpl(PrivateKey privateKey, String keyId, DatabaseTimeService timeService) {
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.keyId = requireText(keyId, "keyId");
        this.timeService = Objects.requireNonNull(timeService, "timeService");
        if (!(privateKey instanceof RSAKey rsaKey) || !"RSA".equals(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("The JWT signing key must be RSA.");
        }
        if (rsaKey.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalArgumentException("The RSA key of the JWT must have at least 2048 bits.");
        }
    }

        /**
     * Issues an RS256 JWT with the claims iss, aud, sub (userId), email, roles, permissions, iat, exp and jti, without
     * including passwords, hashes, names or status (RN-MS01-08)
     *
     * @param userId id of the user
     * @param email email of the user
     * @param roles names of the roles of the user
     * @param permissions effective permissions of the user, the union of those of all their roles
     * @return the signed token together with its expiration date
     * @throws IllegalArgumentException if the email is empty or the user has no role or no permission
     */
    @Override
    public IssuedToken issueToken(UUID userId, String email, List<String> roles, List<String> permissions) {
        Objects.requireNonNull(userId, "userId");
        requireText(email, "email");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("The JWT must include at least one role.");
        }
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("The JWT must include at least one permission.");
        }

        Instant issuedAt = timeService.now();
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
        payload.put("roles", roles.stream().distinct().sorted().toList());
        payload.put("permissions", permissions.stream().distinct().sorted().toList());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("jti", UUID.randomUUID().toString());

        String signingInput = encode(MiniJson.write(header)) + "." + encode(MiniJson.write(payload));
        String signature = BASE64_URL.encodeToString(sign(signingInput));
        return new IssuedToken(signingInput + "." + signature, expiresAt);
    }

        /**
     * Generates the digital signature of the token
     *
     * @param signingInput header and payload encoded in BASE64_URL
     * @return the digital signature
     */
    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JWT could not be signed.", e);
        }
    }

        /**
     * Encodes the received String in BASE64_URL format
     *
     * @param json received string that must be encoded
     * @return returns the BASE64 encoding
     */
    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

        /**
     * Checks that the value of a field is valid (it is required), verifying that it is neither null nor filled with
     * spaces
     *
     * @param value value of a field
     * @param name name of the field
     * @return the received value only when it is considered valid
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The parameter '" + name + "' must not be empty.");
        }
        return value;
    }
}
