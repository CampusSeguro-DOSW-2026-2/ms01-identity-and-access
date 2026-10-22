package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Utilidades para la llave RSA de firma del JWT (JWT_PRIVATE_KEY, formato PEM PKCS#8 como lo genera
 * {@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048}).
 *
 * Los mensajes de error nunca incluyen material de la llave.
 */
public final class RsaKeyUtils {

    private static final String PKCS8_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_END = "-----END PRIVATE KEY-----";
    private static final int GENERATED_KEY_BITS = 2048;

    private RsaKeyUtils() {
    }

    /**
     * Interpreta una llave privada RSA en PEM PKCS#8. Acepta saltos de linea reales o escritos como
     * la secuencia literal {@code \n} (habitual al pasar el PEM por una variable de entorno), y
     * tambien el cuerpo base64 sin las lineas BEGIN/END.
     *
     * @throws IllegalArgumentException si el valor esta vacio o no es una llave RSA PKCS#8 valida
     */
    public static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("La llave privada JWT está vacía.");
        }
        String normalized = pem.replace("\\n", "\n").trim();
        if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException(
                    "La llave privada JWT debe estar en formato PKCS#8 (BEGIN PRIVATE KEY), no PKCS#1.");
        }
        String body = normalized.replace(PKCS8_BEGIN, "").replace(PKCS8_END, "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La llave privada JWT no es un PEM válido.", e);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("La llave privada JWT no es una llave RSA PKCS#8 válida.", e);
        }
    }

    /** Genera un par RSA de 2048 bits (solo para desarrollo cuando no hay llave configurada). */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El JDK no soporta RSA.", e);
        }
    }
}
