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
 * Lectura de JWT_PRIVATE_KEY (PEM PKCS#8) y generacion de la llave efimera de desarrollo
 */
class RsaKeyUtilsTest {

    private static KeyPair keyPair;
    private static String body;
    private static String pem;

    /**
     * Genera el par de llaves que se comparte entre las pruebas
     */
    @BeforeAll
    static void generateKeys() {
        keyPair = RsaKeyUtils.generateKeyPair();
        body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    /**
     * Se lee un PEM PKCS#8 estándar
     */
    @Test
    void parsePrivateKey_readsAStandardPkcs8Pem() {
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(pem);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

    /**
     * Se aceptan saltos de línea escritos como la secuencia literal \n
     */
    @Test
    void parsePrivateKey_acceptsNewlinesWrittenAsLiteralBackslashN() {
        // Asi suele llegar el PEM cuando se define en una sola linea de variable de entorno.
        String oneLine = pem.trim().replace("\n", "\\n");

        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(oneLine);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

    /**
     * Se acepta el cuerpo en base64 sin las líneas BEGIN y END
     */
    @Test
    void parsePrivateKey_acceptsBareBase64WithoutHeaders() {
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey(body);

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

    /**
     * Se toleran los espacios alrededor del PEM
     */
    @Test
    void parsePrivateKey_toleratesSurroundingWhitespace() {
        PrivateKey parsed = RsaKeyUtils.parsePrivateKey("\n\n  " + pem + "  \n");

        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsed.getEncoded());
    }

    /**
     * Un valor nulo o en blanco se rechaza
     */
    @Test
    void parsePrivateKey_blankOrNull_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey(null));
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey(""));
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey("   \n "));
    }

    /**
     * Una llave PKCS#1 se rechaza con un mensaje que indica el formato esperado
     */
    @Test
    void parsePrivateKey_pkcs1_isRejectedWithAHelpfulMessage() {
        String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\n" + body + "\n-----END RSA PRIVATE KEY-----";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RsaKeyUtils.parsePrivateKey(pkcs1));

        assertTrue(ex.getMessage().contains("PKCS#8"));
    }

    /**
     * Un texto que no es una llave se rechaza
     */
    @Test
    void parsePrivateKey_garbage_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey("esto no es una llave ***"));
        // Base64 valido, pero no es una estructura PKCS#8.
        assertThrows(IllegalArgumentException.class, () -> RsaKeyUtils.parsePrivateKey("AAECAwQFBgcICQ=="));
    }

    /**
     * Los mensajes de error nunca incluyen material de la llave
     */
    @Test
    void parsePrivateKey_errorMessagesNeverLeakKeyMaterial() {
        String corrupted = pem.replace(body.substring(0, 40), "####" + body.substring(4, 40));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RsaKeyUtils.parsePrivateKey(corrupted));

        assertFalse(ex.getMessage().contains(body.substring(40, 80)));
    }

    /**
     * El par generado es RSA de 2048 bits
     */
    @Test
    void generateKeyPair_returnsAnRsa2048Pair() {
        KeyPair generated = RsaKeyUtils.generateKeyPair();

        assertEquals("RSA", generated.getPrivate().getAlgorithm());
        assertEquals(2048, ((RSAKey) generated.getPrivate()).getModulus().bitLength());
        assertEquals(2048, ((RSAKey) generated.getPublic()).getModulus().bitLength());
    }
}
