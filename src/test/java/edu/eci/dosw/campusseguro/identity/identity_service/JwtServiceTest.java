package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RsaKeyUtils;

/**
 * Emision del JWT RS256 (RF-MS01-03, 4_jwt-spec.md). No necesita Spring: se prueba contra el JDK.
 */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00.789Z");
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeys() {
        keyPair = RsaKeyUtils.generateKeyPair();
    }

    private static JwtServiceImpl service() {
        return new JwtServiceImpl(keyPair.getPrivate(), "ms01-identity-and-access", "campusseguro", "ms01-test",
                Duration.ofMinutes(60), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String[] parts(String token) {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        return parts;
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private static boolean verifies(java.security.PublicKey key, String[] parts) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }

    // ---------------------------------------------------------------- forma del token

    @Test
    void issueToken_producesCompactJwsWithoutPadding() {
        String token = service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token();

        assertEquals(3, token.split("\\.").length);
        assertFalse(token.contains("="));
        assertTrue(token.matches("[A-Za-z0-9_.-]+"));
    }

    @Test
    void issueToken_headerDeclaresRs256AndKeyId() {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());

        assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"ms01-test\"}", decode(parts[0]));
    }

    @Test
    void issueToken_payloadHasExactlyTheClaimsOfTheSpec() {
        long iat = NOW.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.PROFESOR, UserRole.ESTUDIANTE)).token());

        String expected = Pattern.quote("{\"iss\":\"ms01-identity-and-access\",\"aud\":\"campusseguro\","
                + "\"sub\":\"" + USER_ID + "\",\"email\":\"" + EMAIL + "\","
                + "\"roles\":[\"ESTUDIANTE\",\"PROFESOR\"],\"iat\":" + iat + ",\"exp\":" + (iat + 3600)
                + ",\"jti\":\"") + "[0-9a-f-]{36}" + Pattern.quote("\"}");
        assertTrue(Pattern.matches(expected, decode(parts[1])), decode(parts[1]));
    }

    @Test
    void issueToken_rolesAreSortedAlphabetically() {
        String[] parts = parts(service()
                .issueToken(USER_ID, EMAIL, List.of(UserRole.PROFESOR, UserRole.ADMIN, UserRole.BRIGADISTA)).token());

        assertTrue(decode(parts[1]).contains("\"roles\":[\"ADMIN\",\"BRIGADISTA\",\"PROFESOR\"]"));
    }

    @Test
    void issueToken_jtiIsAUuidAndDiffersBetweenTokens() {
        JwtServiceImpl service = service();
        String first = decode(parts(service.issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token())[1]);
        String second = decode(parts(service.issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token())[1]);

        java.util.regex.Matcher a = Pattern.compile("\"jti\":\"([^\"]+)\"").matcher(first);
        java.util.regex.Matcher b = Pattern.compile("\"jti\":\"([^\"]+)\"").matcher(second);
        assertTrue(a.find());
        assertTrue(b.find());
        assertTrue(UUID_PATTERN.matcher(a.group(1)).matches());
        assertNotEquals(a.group(1), b.group(1));
    }

    // ---------------------------------------------------------------- expiracion

    @Test
    void issueToken_expiresExactlyOneHourAfterIssuance() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE));

        assertEquals(Instant.parse("2026-10-22T06:00:00Z"), issued.expiresAt());
        assertTrue(decode(parts(issued.token())[1]).contains("\"exp\":" + issued.expiresAt().getEpochSecond()));
    }

    @Test
    void issueToken_lifetimeIsConfigurable() {
        JwtServiceImpl shortLived = new JwtServiceImpl(keyPair.getPrivate(), "iss", "aud", "kid",
                Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(Instant.parse("2026-10-22T05:05:00Z"),
                shortLived.issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).expiresAt());
    }

    // ---------------------------------------------------------------- firma

    @Test
    void issueToken_signatureVerifiesWithThePublicKey() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());

        assertTrue(verifies(keyPair.getPublic(), parts));
    }

    @Test
    void issueToken_tamperedPayloadFailsVerification() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());
        String forgedPayload = decode(parts[1]).replace("ESTUDIANTE", "ADMIN");
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8));

        assertFalse(verifies(keyPair.getPublic(), parts));
    }

    @Test
    void issueToken_signatureDoesNotVerifyWithAnotherKey() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());

        assertFalse(verifies(RsaKeyUtils.generateKeyPair().getPublic(), parts));
    }

    // ---------------------------------------------------------------- RN-MS01-08

    @Test
    void issueToken_neverIncludesSensitiveOrNonSpecifiedData() {
        String payload = decode(parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token())[1]);

        for (String forbidden : List.of("password", "contrasena", "hash", "nombre", "apellido", "estado", "permisos")) {
            assertFalse(payload.toLowerCase().contains(forbidden), "El payload no debe contener " + forbidden);
        }
    }

    @Test
    void issueToken_escapesSpecialCharactersSoThePayloadStaysValidJson() {
        String payload = decode(parts(service()
                .issueToken(USER_ID, "a\"b\\c@escuelaing.edu.co", List.of(UserRole.ESTUDIANTE)).token())[1]);

        assertTrue(payload.contains("\"email\":\"a\\\"b\\\\c@escuelaing.edu.co\""), payload);
    }

    // ---------------------------------------------------------------- validaciones

    @Test
    void issueToken_withoutRoles_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class, () -> service.issueToken(USER_ID, EMAIL, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.issueToken(USER_ID, EMAIL, null));
    }

    @Test
    void issueToken_withBlankEmail_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.issueToken(USER_ID, " ", List.of(UserRole.ESTUDIANTE)));
    }

    @Test
    void constructor_rejectsRsaKeysShorterThan2048Bits() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();

        assertThrows(IllegalArgumentException.class, () -> new JwtServiceImpl(weak.getPrivate(), "iss", "aud", "kid",
                Duration.ofMinutes(60), Clock.systemUTC()));
    }

    @Test
    void constructor_rejectsNonRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair ec = generator.generateKeyPair();

        assertThrows(IllegalArgumentException.class, () -> new JwtServiceImpl(ec.getPrivate(), "iss", "aud", "kid",
                Duration.ofMinutes(60), Clock.systemUTC()));
    }

    @Test
    void constructor_rejectsNonPositiveLifetimeAndBlankMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new JwtServiceImpl(keyPair.getPrivate(), "iss", "aud", "kid",
                Duration.ZERO, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new JwtServiceImpl(keyPair.getPrivate(), " ", "aud", "kid",
                Duration.ofMinutes(60), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new JwtServiceImpl(keyPair.getPrivate(), "iss", "", "kid",
                Duration.ofMinutes(60), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new JwtServiceImpl(keyPair.getPrivate(), "iss", "aud", null,
                Duration.ofMinutes(60), Clock.systemUTC()));
    }
}
