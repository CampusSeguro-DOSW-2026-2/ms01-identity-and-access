package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.RSAKey;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RsaKeyUtils;

/**
 * Reading of JWT_PRIVATE_KEY (PEM PKCS#8) and generation of the ephemeral development key
 */
class RsaKeyUtilsTest {

    private static KeyPair keyPair;
    private static String body;
    private static String pem;

        /**
     * Generates the key pair that is shared between the tests
     */
    @BeforeAll
    static void generateKeys() {
        keyPair = RsaKeyUtils.generateKeyPair();
        body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

        /**
     * A standard PKCS#8 PEM is read
     */
    @Test
    void parsePrivateKey_readsAStandardPkcs8Pem() {
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(pem);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

        /**
     * Line breaks written as the literal sequence \n are accepted
     */
    @Test
    void parsePrivateKey_acceptsNewlinesWrittenAsLiteralBackslashN() {
        // This is how the PEM usually arrives when it is defined in a single line of an environment variable.
        String oneLine = pem.trim().replace("\n", "\\n");

        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(oneLine);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

        /**
     * The base64 body without the BEGIN and END lines is accepted
     */
    @Test
    void parsePrivateKey_acceptsBareBase64WithoutHeaders() {
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(body);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

        /**
     * The spaces around the PEM are tolerated
     */
    @Test
    void parsePrivateKey_toleratesSurroundingWhitespace() {
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey("\n\n  " + pem + "  \n");

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

        /**
     * A null or blank value is rejected
     */
    @Test
    void parsePrivateKey_blankOrNull_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey(null));
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey(""));
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey("   \n "));
    }

        /**
     * A PKCS#1 key is rejected with a message that indicates the expected format
     */
    @Test
    void parsePrivateKey_pkcs1_isRejectedWithAHelpfulMessage() {
        String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\n" + body + "\n-----END RSA PRIVATE KEY-----";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RsaKeyUtils.parsePrivateKey(pkcs1));

        assertTrue(ex.getMessage().contains("PKCS#8"));
    }

        /**
     * A text that is not a key is rejected
     */
    @Test
    void parsePrivateKey_garbage_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey("esto no es una llave ***"));
        // Valid Base64, but it is not a PKCS#8 structure.
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey("AAECAwQFBgcICQ=="));
    }

        /**
     * The error messages never include key material
     */
    @Test
    void parsePrivateKey_errorMessagesNeverLeakKeyMaterial() {
        String corrupted = pem.replace(body.substring(0, 40), "####" + body.substring(4, 40));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RsaKeyUtils.parsePrivateKey(corrupted));

        assertFalse(ex.getMessage().contains(body.substring(40, 80)));
    }

        /**
     * The generated pair is a 2048-bit RSA
     */
    @Test
    void generateKeyPair_returnsAnRsa2048Pair() {
        KeyPair generated = RsaKeyUtils.generateKeyPair();

        assertEquals("RSA", generated.getPrivate().getAlgorithm());
        assertEquals(2048, ((RSAKey) generated.getPrivate()).getModulus().bitLength());
        assertEquals(2048, ((RSAKey) generated.getPublic()).getModulus().bitLength());
    }
}
