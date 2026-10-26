package edu.eci.dosw.campusseguro.identity.identity_service.util;

/**
 * Utilidades de texto para aplicar la regla general de entrada RG-01 (los textos se recortan antes de
 * validar)
 */
public final class Texts {

    /**
     * Clase de utilidad, no se instancia
     */
    private Texts() {
    }

    /**
     * Recorta los espacios al inicio y al final de un texto y lo deja en null si queda vacío, así un
     * campo obligatorio vacío o con solo espacios se considera faltante (RG-02) y un campo opcional vacío se
     * considera omitido
     *
     * @param value texto recibido, puede ser null
     * @return el texto recortado, o null si el valor era null o quedó vacío
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
