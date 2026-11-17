package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RsaKeyUtils;
import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * Issuance of the RS256 JWT (RF-MS01-03, 4_jwt-spec.md). It does not need Spring: it is tested against the JDK
 */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00.789Z");
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final List<String> PERMISSIONS = List.of("ms01:role:read_own", "ms01:profile:read");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static KeyPair keyPair;

        /**
     * Generates the key pair that is shared between the tests
     */
    @BeforeAll
    static void generateKeys() {
        keyPair = RsaKeyUtils.generateKeyPair();
    }

        /**
     * Creates the token issuer with a fixed clock and a validity of one hour
     *
     * @return the token issuer
     */
    private static JwtServiceImpl service() {
        return new JwtServiceImpl(keyPair.getPrivate(), "ms01-test", FixedDatabaseTime.at(NOW));
    }

        /**
     * Splits a token into its three parts
     *
     * @param token compact token
     * @return header, body and signature
     */
    private static String[] parts(String token) {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        return parts;
    }

        /**
     * Decodes a part of the token into text
     *
     * @param part part encoded in base64 URL
     * @return the decoded text
     */
    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

        /**
     * Verifies the signature of a token with a public key
     *
     * @param key public key with which it is verified
     * @param parts parts of the token
     * @return true if the signature is valid
     * @throws Exception if the verifier cannot be initialized
     */
    private static boolean verifies(java.security.PublicKey key, String[] parts) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }

    // ---------------------------------------------------------------- shape of the token

        /**
     * The token is a compact JWS without base64 padding
     */
    @Test
    void issueToken_producesCompactJwsWithoutPadding() {
        String token = service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token();

        assertEquals(3, token.split("\\.").length);
        assertFalse(token.contains("="));
        assertTrue(token.matches("[A-Za-z0-9_.-]+"));
    }

        /**
     * The header declares RS256 and the identifier of the key
     */
    @Test
    void issueToken_headerDeclaresRs256AndKeyId() {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token());

        assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"ms01-test\"}", decode(parts[0]));
    }

        /**
     * The body has exactly the claims of the specification
     */
    @Test
    void issueToken_payloadHasExactlyTheClaimsOfTheSpec() {
        long iat = NOW.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        String[] parts = parts(service()
                .issueToken(USER_ID, EMAIL, List.of("PROFESOR", "ESTUDIANTE"), PERMISSIONS)
                .token());

        String expected = Pattern.quote("{\"iss\":\"ms01-identity-and-access\",\"aud\":\"campusseguro\","
                + "\"sub\":\"" + USER_ID + "\",\"email\":\"" + EMAIL + "\","
                + "\"roles\":[\"ESTUDIANTE\",\"PROFESOR\"],"
                + "\"permissions\":[\"ms01:profile:read\",\"ms01:role:read_own\"],\"iat\":" + iat
                + ",\"exp\":" + (iat + 3600) + ",\"jti\":\"") + "[0-9a-f-]{36}" + Pattern.quote("\"}");
        assertTrue(Pattern.matches(expected, decode(parts[1])), decode(parts[1]));
    }

        /**
     * The permissions of the token are in alphabetical order and without repeated ones
     */
    @Test
    void issueToken_permissionsAreSortedAndDistinct() {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"),
                List.of("ms05:panel:read_own", "ms01:profile:read", "ms05:panel:read_own")).token());

        assertTrue(decode(parts[1]).contains("\"permissions\":[\"ms01:profile:read\",\"ms05:panel:read_own\"]"),
                decode(parts[1]));
    }

        /**
     * The roles of the token are in alphabetical order
     */
    @Test
    void issueToken_rolesAreSortedAlphabetically() {
        String[] parts = parts(service()
                .issueToken(USER_ID, EMAIL, List.of("PROFESOR", "ADMIN", "BRIGADISTA"), PERMISSIONS).token());

        assertTrue(decode(parts[1]).contains("\"roles\":[\"ADMIN\",\"BRIGADISTA\",\"PROFESOR\"]"));
    }

        /**
     * The jti is a UUID and changes between tokens
     */
    @Test
    void issueToken_jtiIsAUuidAndDiffersBetweenTokens() {
        JwtServiceImpl service = service();
        String first = decode(parts(service.issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token())[1]);
        String second = decode(
                parts(service.issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token())[1]);

        java.util.regex.Matcher a = Pattern.compile("\"jti\":\"([^\"]+)\"").matcher(first);
        java.util.regex.Matcher b = Pattern.compile("\"jti\":\"([^\"]+)\"").matcher(second);
        assertTrue(a.find());
        assertTrue(b.find());
        assertTrue(UUID_PATTERN.matcher(a.group(1)).matches());
        assertNotEquals(a.group(1), b.group(1));
    }

    // ---------------------------------------------------------------- expiration

        /**
     * The token expires exactly one hour after being issued
     */
    @Test
    void issueToken_expiresExactlyOneHourAfterIssuance() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS);

        assertEquals(Instant.parse("2026-10-22T06:00:00Z"), issued.expiresAt());
        assertTrue(decode(parts(issued.token())[1]).contains("\"exp\":" + issued.expiresAt().getEpochSecond()));
    }

        /**
     * The validity of the token is the one set by the Integration Standards and does not depend on the configuration
     */
    @Test
    void issueToken_lifetimeIsTheGlobalValue() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS);

        assertEquals(NOW.truncatedTo(ChronoUnit.SECONDS).plusSeconds(GlobalValues.JWT_LIFETIME_SECONDS),
                issued.expiresAt());
    }

    // ---------------------------------------------------------------- signature

        /**
     * The signature is verified with the public key
     *
     * @throws Exception if the verification of the signature fails
     */
    @Test
    void issueToken_signatureVerifiesWithThePublicKey() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token());

        assertTrue(verifies(keyPair.getPublic(), parts));
    }

        /**
     * An altered body does not pass the signature verification
     *
     * @throws Exception if the verification of the signature fails
     */
    @Test
    void issueToken_tamperedPayloadFailsVerification() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token());
        String forgedPayload = decode(parts[1]).replace("ESTUDIANTE", "ADMIN");
        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8));

        assertFalse(verifies(keyPair.getPublic(), parts));
    }

        /**
     * The signature is not verified with another key
     *
     * @throws Exception if the verification of the signature fails
     */
    @Test
    void issueToken_signatureDoesNotVerifyWithAnotherKey() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token());

        assertFalse(verifies(RsaKeyUtils.generateKeyPair().getPublic(), parts));
    }

    // ---------------------------------------------------------------- RN-MS01-08

        /**
     * The token never includes sensitive data or data that is not in the specification
     */
    @Test
    void issueToken_neverIncludesSensitiveOrNonSpecifiedData() {
        String payload = decode(
                parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token())[1]);

        for (String forbidden : List.of("password", "contrasena", "hash", "nombre", "apellido", "estado", "permisos",
                "valido_desde", "es_sistema")) {
            assertFalse(payload.toLowerCase().contains(forbidden), "El payload no debe contener " + forbidden);
        }
    }

        /**
     * The special characters are escaped so the body remains a valid JSON
     */
    @Test
    void issueToken_escapesSpecialCharactersSoThePayloadStaysValidJson() {
        String payload = decode(parts(service()
                .issueToken(USER_ID, "a\"b\\c@escuelaing.edu.co", List.of("ESTUDIANTE"), PERMISSIONS).token())[1]);

        assertTrue(payload.contains("\"email\":\"a\\\"b\\\\c@escuelaing.edu.co\""), payload);
    }

    // ---------------------------------------------------------------- validations

        /**
     * A user without roles is rejected
     */
    @Test
    void issueToken_withoutRoles_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class, () -> service.issueToken(USER_ID, EMAIL, List.of(), PERMISSIONS));
        assertThrows(IllegalArgumentException.class, () -> service.issueToken(USER_ID, EMAIL, null, PERMISSIONS));
    }

        /**
     * A user without permissions is rejected, a token without permissions would not authorize anything
     */
    @Test
    void issueToken_withoutPermissions_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), null));
    }

        /**
     * A blank email is rejected
     */
    @Test
    void issueToken_withBlankEmail_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.issueToken(USER_ID, " ", List.of("ESTUDIANTE"), PERMISSIONS));
    }

        /**
     * RSA keys of less than 2048 bits are rejected
     *
     * @throws Exception if the test key cannot be generated
     */
    @Test
    void constructor_rejectsRsaKeysShorterThan2048Bits() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();

        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(weak.getPrivate(), "kid", FixedDatabaseTime.at(NOW)));
    }

        /**
     * Keys that are not RSA are rejected
     *
     * @throws Exception if the test key cannot be generated
     */
    @Test
    void constructor_rejectsNonRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair ec = generator.generateKeyPair();

        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(ec.getPrivate(), "kid", FixedDatabaseTime.at(NOW)));
    }

        /**
     * A blank or null key identifier is rejected
     */
    @Test
    void constructor_rejectsABlankKeyId() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(keyPair.getPrivate(), " ", FixedDatabaseTime.at(NOW)));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(keyPair.getPrivate(), "", FixedDatabaseTime.at(NOW)));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(keyPair.getPrivate(), null, FixedDatabaseTime.at(NOW)));
    }

    // ---------------------------------------------------------------- validity

        /**
     * The validity is exactly 3600 seconds, the maximum allowed by the Integration Standards
     */
    @Test
    void issueToken_lifetimeIs3600Seconds() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS);

        assertEquals(NOW.truncatedTo(ChronoUnit.SECONDS).plusSeconds(3600), issued.expiresAt());
    }

        /**
     * The issuer and the audience of the token are the global values of the Integration Standards
     */
    @Test
    void issueToken_issuerAndAudienceAreTheGlobalValues() {
        String payload = decode(
                parts(service().issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS).token())[1]);

        assertTrue(payload.contains("\"iss\":\"" + GlobalValues.JWT_ISSUER + "\""), payload);
        assertTrue(payload.contains("\"aud\":\"" + GlobalValues.JWT_AUDIENCE + "\""), payload);
    }

        /**
     * The iat and the exp come from the database time, which is queried only once per token so both claims start from
     * the same instant
     */
    @Test
    void issueToken_iatAndExpComeFromTheDatabaseTimeQueriedOncePerToken() {
        DatabaseTimeService databaseTime = mock(DatabaseTimeService.class);
        Instant databaseNow = Instant.parse("2026-11-12T17:45:30Z");
        when(databaseTime.now()).thenReturn(databaseNow);
        JwtServiceImpl service = new JwtServiceImpl(keyPair.getPrivate(), "ms01-test", databaseTime);

        IssuedToken issued = service.issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS);

        String payload = decode(parts(issued.token())[1]);
        assertTrue(payload.contains("\"iat\":" + databaseNow.getEpochSecond()), payload);
        assertTrue(payload.contains("\"exp\":" + (databaseNow.getEpochSecond() + 3600)), payload);
        assertEquals(databaseNow.plusSeconds(3600), issued.expiresAt());
        verify(databaseTime, times(1)).now();
    }

        /**
     * If the database does not respond, the token is not issued and the error is propagated
     */
    @Test
    void issueToken_databaseTimeFailure_isPropagated() {
        DatabaseTimeService databaseTime = mock(DatabaseTimeService.class);
        when(databaseTime.now()).thenThrow(new IllegalStateException("no time"));
        JwtServiceImpl service = new JwtServiceImpl(keyPair.getPrivate(), "ms01-test", databaseTime);

        assertThrows(IllegalStateException.class,
                () -> service.issueToken(USER_ID, EMAIL, List.of("ESTUDIANTE"), PERMISSIONS));
    }
}
