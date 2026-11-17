package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * RG-08: format of the stored role names, normalization of the received names and alphabetical order of the roles of a
 * user
 */
class RoleNamesTest {

        /**
     * The two system roles that the code needs to know
     */
    @Test
    void systemRoleNames_areEstudianteAndAdmin() {
        assertEquals("ESTUDIANTE", RoleNames.STUDENT);
        assertEquals("ADMIN", RoleNames.ADMIN);
    }

        /**
     * DETAILS is reserved: it is the literal segment of the route GET /roles/details
     */
    @Test
    void reservedName_isDetails() {
        assertEquals("DETAILS", RoleNames.RESERVED);
        assertTrue(RoleNames.isReserved("DETAILS"));
    }

        /**
     * Only the exact stored name is reserved: other names, lowercase, prefixes and null are not
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ADMIN", "DETAIL", "DETAILS_X", "XDETAILS", "details", "Details", " DETAILS"})
    void isReserved_otherNames_areNotReserved(String name) {
        assertFalse(RoleNames.isReserved(name));
    }

        /**
     * A name received in lowercase is normalized to DETAILS and then it is reserved
     */
    @Test
    void normalize_detailsInAnyCase_becomesTheReservedName() {
        assertTrue(RoleNames.isReserved(RoleNames.normalize("details")));
        assertTrue(RoleNames.isReserved(RoleNames.normalize(" Details ")));
    }

        /**
     * A stored name is uppercase letters, digits and underscore, from 2 to 30 characters and starts with a letter
     */
    @ParameterizedTest
    @ValueSource(strings = {"AB", "ADMIN", "MONITOR_EVACUACION", "ROL_2", "A_1", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234"})
    void hasStoredFormat_validNames_areAccepted(String name) {
        assertTrue(RoleNames.hasStoredFormat(name));
    }

        /**
     * Lowercase, spaces, symbols, a non-alphabetic start and lengths out of range are not a stored name
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "A", "admin", "Admin", " ADMIN", "ADMIN ", "1ADMIN", "_ADMIN", "AD-MIN", "AD MIN",
            "ADMINÑ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345"})
    void hasStoredFormat_invalidNames_areRejected(String name) {
        assertFalse(RoleNames.hasStoredFormat(name));
    }

        /**
     * A received name is trimmed and converted to uppercase
     */
    @Test
    void normalize_trimsAndUppercases() {
        assertEquals("MONITOR_EVACUACION", RoleNames.normalize("  monitor_evacuacion "));
        assertEquals("ADMIN", RoleNames.normalize("Admin"));
        assertEquals("ROL_2", RoleNames.normalize("rol_2"));
    }

        /**
     * A received name with an invalid format gives null
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "a", "1rol", "_rol", "rol-nuevo", "rol nuevo", "ñandú",
            "abcdefghijklmnopqrstuvwxyz12345"})
    void normalize_invalidNames_returnNull(String raw) {
        assertNull(RoleNames.normalize(raw));
    }

        /**
     * The roles are sorted alphabetically regardless of the order of the received set
     */
    @Test
    void sorted_isAlphabetical() {
        Set<String> received = new HashSet<>(List.of("PROFESOR", "ADMIN", "ESTUDIANTE", "BRIGADISTA"));

        assertEquals(List.of("ADMIN", "BRIGADISTA", "ESTUDIANTE", "PROFESOR"), RoleNames.sorted(received));
    }

        /**
     * An empty set gives an empty list
     */
    @Test
    void sorted_empty_returnsAnEmptyList() {
        assertTrue(RoleNames.sorted(Set.of()).isEmpty());
    }

        /**
     * It does not modify the received set
     */
    @Test
    void sorted_doesNotModifyTheReceivedSet() {
        Set<String> received = new HashSet<>(List.of("PROFESOR", "ADMIN"));

        RoleNames.sorted(received);

        assertEquals(Set.of("PROFESOR", "ADMIN"), received);
    }

        /**
     * The returned list is read-only
     */
    @Test
    void sorted_resultIsReadOnly() {
        List<String> sorted = RoleNames.sorted(Set.of("ADMIN"));

        assertThrows(UnsupportedOperationException.class, () -> sorted.add("PROFESOR"));
    }
}
