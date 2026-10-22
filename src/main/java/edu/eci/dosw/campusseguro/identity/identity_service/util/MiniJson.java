package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Serializador JSON minimo y determinista.
 *
 * Se usa para el encabezado y el cuerpo del JWT y para el cuerpo del evento de auditoria, donde la
 * forma es fija y pequena. Asi la emision del token no depende de la version de Jackson ni de su
 * configuracion. Soporta: null, CharSequence, Boolean, Number, Enum, Map y cualquier Iterable.
 */
public final class MiniJson {

    private MiniJson() {
    }

    /**
     * Serializa un objeto soportado en una cadena de texto con formato JSON.
     * 
     * @param value el objeto a serializar (puede ser Map, Iterable, Number, Boolean, Enum, CharSequence o null
     * @return una cadena de texto que representa el objeto en formato JSON
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(value, out);
        return out.toString();
    }

    /**
     * Evalúa el tipo del objeto recibido y lo serializa adecuadamente, acumulando el resultado 
     * en el acumulador de texto proporcionado.
     * 
     * @param value el objeto actual a procesar y adjuntar
     * @param out el constructor de cadenas acumulativo donde se escribe el resultado
     */
    private static void append(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence text) {
            appendString(text.toString(), out);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Enum<?> constant) {
            appendString(constant.name(), out);
        } else if (value instanceof Number number) {
            appendNumber(number, out);
        } else if (value instanceof Map<?, ?> map) {
            appendObject(map, out);
        } else if (value instanceof Iterable<?> items) {
            appendArray(items, out);
        } else {
            throw new IllegalArgumentException("Tipo no soportado para JSON: " + value.getClass().getName());
        }
    }

    /**
     * Serializa un mapa de tipo llave-valor en un objeto JSON con llaves delimitadas por llaves {}.
     * 
     * @param map el mapa de entradas a serializar como propiedades del objeto JSON
     * @param out el acumulador de cadenas donde se escribe el objeto JSON resultante
     */
    private static void appendObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(String.valueOf(entry.getKey()), out);
            out.append(':');
            append(entry.getValue(), out);
        }
        out.append('}');
    }

    /**
     * Serializa una colección iterable de elementos en un arreglo JSON delimitado por corchetes [].
     * 
     * @param items la colección de elementos a serializar
     * @param out el acumulador de cadenas donde se escribe el arreglo JSON resultante
     */
    private static void appendArray(Iterable<?> items, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(item, out);
        }
        out.append(']');
    }

    /**
     * Serializa un valor numérico asegurando un formato válido para JSON (manejando de forma plana 
     * los decimales y restringiendo valores no válidos como NaN o infinitos).
     * 
     * @param number el objeto numérico a serializar
     * @param out el acumulador de cadenas donde se adjunta la representación numérica
     */
    private static void appendNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("NaN e infinito no son valores JSON validos.");
            }
            out.append(number);
        } else if (number instanceof BigDecimal decimal) {
            out.append(decimal.toPlainString());
        } else {
            out.append(number);
        }
    }

    /**
     * Serializa una cadena de texto escapando correctamente los caracteres especiales 
     * exigidos por el estándar JSON y delimitándola entre comillas dobles.
     * 
     * @param text la cadena de texto plano a escapar y serializar
     * @param out el acumulador de cadenas donde se escribe el texto formateado
     */
    private static void appendString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
