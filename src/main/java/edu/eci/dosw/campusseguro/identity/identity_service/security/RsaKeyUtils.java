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
 * Utilities for the RSA signing key of the JWT (JWT_PRIVATE_KEY, PEM PKCS#8 format)
 *
 * The error messages never include key material
 */
public final class RsaKeyUtils {

    private static final String PKCS8_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_END = "-----END PRIVATE KEY-----";
    private static final int GENERATED_KEY_BITS = 2048;

        /**
     * Utility class, it is not instantiated
     */
    private RsaKeyUtils() {
    }

        /**
     * Parses an RSA private key in PEM PKCS#8, it accepts real line breaks or written as the literal sequence \n (usual
     * when passing the PEM through an environment variable) and also the base64 body without the BEGIN/END lines
     *
     * @param pem text in PEM format
     * @return the PrivateKey used by the login module
     * @throws IllegalArgumentException if the value is empty or is not a valid RSA PKCS#8 key
     */
    public static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("The JWT private key is empty.");
        }
        String normalized = pem.replace("\\n", "\n").trim();
        if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException(
                    "The JWT private key must be in PKCS#8 format (BEGIN PRIVATE KEY), not PKCS#1.");
        }
        String body = normalized.replace(PKCS8_BEGIN, "").replace(PKCS8_END, "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The JWT private key is not a valid PEM.", e);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("The JWT private key is not a valid RSA PKCS#8 key.", e);
        }
    }

        /**
     * Generates a 2048-bit RSA pair used only in development, not for the final application
     *
     * @return an RSA cryptographic key pair
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK does not support RSA.", e);
        }
    }
}
