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
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * Emision del JWT RS256 (RF-MS01-03, 4_jwt-spec.md). No necesita Spring: se prueba contra el JDK
 */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00.789Z");
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static KeyPair keyPair;

    /**
     * Genera el par de llaves que se comparte entre las pruebas
     */
    @BeforeAll
    static void generateKeys() {
        keyPair = RsaKeyUtils.generateKeyPair();
    }

    /**
     * Crea el emisor de tokens con reloj fijo y vigencia de una hora
     *
     * @return el emisor de tokens
     */
    private static JwtServiceImpl service() {
        return new JwtServiceImpl(keyPair.getPrivate(), "ms01-test", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Separa un token en sus tres partes
     *
     * @param token token compacto
     * @return encabezado, cuerpo y firma
     */
    private static String[] parts(String token) {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        return parts;
    }

    /**
     * Decodifica una parte del token a texto
     *
     * @param part parte codificada en base64 URL
     * @return el texto decodificado
     */
    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    /**
     * Verifica la firma de un token con una llave pública
     *
     * @param key llave pública con la que se verifica
     * @param parts partes del token
     * @return true si la firma es válida
     * @throws Exception si el verificador no se puede inicializar
     */
    private static boolean verifies(java.security.PublicKey key, String[] parts) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }

    // ---------------------------------------------------------------- forma del token

    /**
     * El token es un JWS compacto sin relleno de base64
     */
    @Test
    void issueToken_producesCompactJwsWithoutPadding() {
        String token = service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token();

        assertEquals(3, token.split("\\.").length);
        assertFalse(token.contains("="));
        assertTrue(token.matches("[A-Za-z0-9_.-]+"));
    }

    /**
     * El encabezado declara RS256 y el identificador de la llave
     */
    @Test
    void issueToken_headerDeclaresRs256AndKeyId() {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());

        assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"ms01-test\"}", decode(parts[0]));
    }

    /**
     * El cuerpo tiene exactamente los claims de la especificación
     */
    @Test
    void issueToken_payloadHasExactlyTheClaimsOfTheSpec() {
        long iat = NOW.truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        String[] parts = parts(service()
                .issueToken(USER_ID, EMAIL, List.of(UserRole.PROFESOR, UserRole.ESTUDIANTE))
                .token());

        String expected = Pattern.quote("{\"iss\":\"ms01-identity-and-access\",\"aud\":\"campusseguro\","
                + "\"sub\":\"" + USER_ID + "\",\"email\":\"" + EMAIL + "\","
                + "\"roles\":[\"ESTUDIANTE\",\"PROFESOR\"],\"iat\":" + iat + ",\"exp\":" + (iat + 3600)
                + ",\"jti\":\"") + "[0-9a-f-]{36}" + Pattern.quote("\"}");
        assertTrue(Pattern.matches(expected, decode(parts[1])), decode(parts[1]));
    }

    /**
     * Los roles del token van en orden alfabético
     */
    @Test
    void issueToken_rolesAreSortedAlphabetically() {
        String[] parts = parts(service()
                .issueToken(USER_ID, EMAIL, List.of(UserRole.PROFESOR, UserRole.ADMIN, UserRole.BRIGADISTA)).token());

        assertTrue(decode(parts[1]).contains("\"roles\":[\"ADMIN\",\"BRIGADISTA\",\"PROFESOR\"]"));
    }

    /**
     * El jti es un UUID y cambia entre tokens
     */
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

    /**
     * El token expira exactamente una hora después de emitirse
     */
    @Test
    void issueToken_expiresExactlyOneHourAfterIssuance() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE));

        assertEquals(Instant.parse("2026-10-22T06:00:00Z"), issued.expiresAt());
        assertTrue(decode(parts(issued.token())[1]).contains("\"exp\":" + issued.expiresAt().getEpochSecond()));
    }

    /**
     * La vigencia del token es la que fijan los Estándares de Integración y no depende de la configuración
     */
    @Test
    void issueToken_lifetimeIsTheGlobalValue() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE));

        assertEquals(NOW.truncatedTo(ChronoUnit.SECONDS).plusSeconds(GlobalValues.JWT_LIFETIME_SECONDS),
                issued.expiresAt());
    }

    // ---------------------------------------------------------------- firma

    /**
     * La firma se verifica con la llave pública
     *
     * @throws Exception si falla la verificación de la firma
     */
    @Test
    void issueToken_signatureVerifiesWithThePublicKey() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());

        assertTrue(verifies(keyPair.getPublic(), parts));
    }

    /**
     * Un cuerpo alterado no pasa la verificación de la firma
     *
     * @throws Exception si falla la verificación de la firma
     */
    @Test
    void issueToken_tamperedPayloadFailsVerification() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());
        String forgedPayload = decode(parts[1]).replace("ESTUDIANTE", "ADMIN");
        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8));

        assertFalse(verifies(keyPair.getPublic(), parts));
    }

    /**
     * La firma no se verifica con otra llave
     *
     * @throws Exception si falla la verificación de la firma
     */
    @Test
    void issueToken_signatureDoesNotVerifyWithAnotherKey() throws Exception {
        String[] parts = parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token());

        assertFalse(verifies(RsaKeyUtils.generateKeyPair().getPublic(), parts));
    }

    // ---------------------------------------------------------------- RN-MS01-08

    /**
     * El token nunca incluye datos sensibles ni datos que no estén en la especificación
     */
    @Test
    void issueToken_neverIncludesSensitiveOrNonSpecifiedData() {
        String payload = decode(parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token())[1]);

        for (String forbidden : List.of("password", "contrasena", "hash", "nombre", "apellido", "estado", "permisos")) {
            assertFalse(payload.toLowerCase().contains(forbidden), "El payload no debe contener " + forbidden);
        }
    }

    /**
     * Los caracteres especiales se escapan para que el cuerpo siga siendo un JSON válido
     */
    @Test
    void issueToken_escapesSpecialCharactersSoThePayloadStaysValidJson() {
        String payload = decode(parts(service()
                .issueToken(USER_ID, "a\"b\\c@escuelaing.edu.co", List.of(UserRole.ESTUDIANTE)).token())[1]);

        assertTrue(payload.contains("\"email\":\"a\\\"b\\\\c@escuelaing.edu.co\""), payload);
    }

    // ---------------------------------------------------------------- validaciones

    /**
     * Un usuario sin roles se rechaza
     */
    @Test
    void issueToken_withoutRoles_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class, () -> service.issueToken(USER_ID, EMAIL, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.issueToken(USER_ID, EMAIL, null));
    }

    /**
     * Un correo en blanco se rechaza
     */
    @Test
    void issueToken_withBlankEmail_isRejected() {
        JwtServiceImpl service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.issueToken(USER_ID, " ", List.of(UserRole.ESTUDIANTE)));
    }

    /**
     * Se rechazan las llaves RSA de menos de 2048 bits
     *
     * @throws Exception si no se puede generar la llave de prueba
     */
    @Test
    void constructor_rejectsRsaKeysShorterThan2048Bits() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();

        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(weak.getPrivate(), "kid", Clock.systemUTC()));
    }

    /**
     * Se rechazan las llaves que no son RSA
     *
     * @throws Exception si no se puede generar la llave de prueba
     */
    @Test
    void constructor_rejectsNonRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair ec = generator.generateKeyPair();

        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(ec.getPrivate(), "kid", Clock.systemUTC()));
    }

    /**
     * Se rechaza un identificador de llave en blanco o nulo
     */
    @Test
    void constructor_rejectsABlankKeyId() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(keyPair.getPrivate(), " ", Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(keyPair.getPrivate(), "", Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtServiceImpl(keyPair.getPrivate(), null, Clock.systemUTC()));
    }

    // ---------------------------------------------------------------- vigencia

    /**
     * La vigencia es exactamente de 3600 segundos, el máximo que permiten los Estándares de Integración
     */
    @Test
    void issueToken_lifetimeIs3600Seconds() {
        IssuedToken issued = service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE));

        assertEquals(NOW.truncatedTo(ChronoUnit.SECONDS).plusSeconds(3600), issued.expiresAt());
    }

    /**
     * El emisor y la audiencia del token son los valores globales de los Estándares de Integración
     */
    @Test
    void issueToken_issuerAndAudienceAreTheGlobalValues() {
        String payload = decode(parts(service().issueToken(USER_ID, EMAIL, List.of(UserRole.ESTUDIANTE)).token())[1]);

        assertTrue(payload.contains("\"iss\":\"" + GlobalValues.JWT_ISSUER + "\""), payload);
        assertTrue(payload.contains("\"aud\":\"" + GlobalValues.JWT_AUDIENCE + "\""), payload);
    }
}
