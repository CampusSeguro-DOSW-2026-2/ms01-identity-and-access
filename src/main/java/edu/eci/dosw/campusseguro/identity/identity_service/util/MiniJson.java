package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Minimal and deterministic JSON serializer
 *
 * It is used for the header and the body of the JWT and for the body of the audit event, where the shape is fixed and
 * small. This way the token issuance does not depend on the Jackson version or its configuration. It supports: null,
 * CharSequence, Boolean, Number, Enum, Map and any Iterable
 */
public final class MiniJson {

        /**
     * It is not instantiated
     */
    private MiniJson() {
    }

        /**
     * Serializes a supported object into a text string in JSON format
     *
     * @param value the object to serialize, may be Map, Iterable, Number, Boolean, Enum, CharSequence or null
     * @return a text string that represents the object in JSON format
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(value, out);
        return out.toString();
    }

        /**
     * Evaluates the type of the received object and serializes it accordingly, accumulating the result in the provided
     * text accumulator
     *
     * @param value the current object to process and append
     * @param out the accumulating string builder where the result is written
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
            throw new IllegalArgumentException("Unsupported type for JSON: " + value.getClass().getName());
        }
    }

        /**
     * Serializes a key-value map into a JSON object with keys delimited by braces {}
     *
     * @param map the map of entries to serialize as properties of the JSON object
     * @param out the string accumulator where the resulting JSON object is written
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
     * Serializes an iterable collection of elements into a JSON array delimited by brackets []
     *
     * @param items the collection of elements to serialize
     * @param out the string accumulator where the resulting JSON array is written
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
     * Serializes a numeric value ensuring a valid JSON format, handling decimals plainly and restricting invalid values
     * such as NaN or infinite ones
     *
     * @param number the numeric object to serialize
     * @param out the string accumulator where the numeric representation is appended
     */
    private static void appendNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("NaN and infinity are not valid JSON values.");
            }
            out.append(number);
        } else if (number instanceof BigDecimal decimal) {
            out.append(decimal.toPlainString());
        } else {
            out.append(number);
        }
    }

        /**
     * Serializes a text string, correctly escaping the special characters required by the JSON standard and delimiting
     * it between double quotes
     *
     * @param text the plain text string to escape and serialize
     * @param out the string accumulator where the formatted text is written
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
