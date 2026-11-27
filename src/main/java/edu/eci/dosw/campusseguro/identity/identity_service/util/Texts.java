package edu.eci.dosw.campusseguro.identity.identity_service.util;

/**
 * Text utilities to apply the general input rule RG-01 (texts are trimmed before validating)
 */
public final class Texts {

        /**
     * Utility class, it is not instantiated
     */
    private Texts() {
    }

        /**
     * Trims the spaces at the beginning and at the end of a text and leaves it as null if it ends up empty, so a
     * required field that is empty or has only spaces is considered missing (RG-02) and an optional field that is empty
     * is considered omitted
     *
     * @param value text received, may be null
     * @return the trimmed text, or null if the value was null or ended up empty
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
