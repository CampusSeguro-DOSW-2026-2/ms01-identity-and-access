package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Role names of CampusSeguro: the two system roles that the code needs to know, the reserved name, the format of a
 * stored name and the normalization of a received name (RG-08)
 */
public final class RoleNames {

    public static final String STUDENT = "ESTUDIANTE";
    public static final String ADMIN = "ADMIN";
    public static final String RESERVED = "DETAILS";

    private static final Pattern STORED_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final Pattern ENTRY_FORMAT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,29}$");

        /**
     * Utility class, it is not instantiated
     */
    private RoleNames() {
    }

        /**
     * Tells whether a name has the format of a stored role, the same as the roles table and the X-User-Role header:
     * uppercase letters, digits and underscore, from 2 to 30 characters and starting with a letter
     *
     * @param name name to check, may be null
     * @return true if the name is a role name with a valid format, whether it exists or not
     */
    public static boolean hasStoredFormat(String name) {
        return name != null && STORED_FORMAT.matcher(name).matches();
    }

        /**
     * Tells whether a name is reserved and cannot be given to a role, it is the fixed segment of the route that queries
     * the detail of the roles
     *
     * @param name already normalized name, may be null
     * @return true if the name is the reserved one
     */
    public static boolean isReserved(String name) {
        return RESERVED.equals(name);
    }

        /**
     * Normalizes the name of a role received in a route or in a body (RG-08): trims the spaces, validates the input
     * format and converts it to uppercase
     *
     * @param raw name received, may be null
     * @return the name in uppercase, or null if it is null or does not have a valid format
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!ENTRY_FORMAT.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

        /**
     * Sorts role names alphabetically
     *
     * @param roles role names
     * @return new list with the names in alphabetical order
     */
    public static List<String> sorted(Collection<String> roles) {
        return roles.stream()
                .sorted()
                .toList();
    }
}
