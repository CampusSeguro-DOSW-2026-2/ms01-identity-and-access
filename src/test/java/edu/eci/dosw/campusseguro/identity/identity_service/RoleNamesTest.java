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
 * RG-08: formato de los nombres de rol guardados, normalización de los nombres recibidos y orden alfabético de
 * los roles de un usuario
 */
class RoleNamesTest {

    /**
     * Los dos roles de sistema que el código necesita conocer
     */
    @Test
    void systemRoleNames_areEstudianteAndAdmin() {
        assertEquals("ESTUDIANTE", RoleNames.STUDENT);
        assertEquals("ADMIN", RoleNames.ADMIN);
    }

    /**
     * DETAILS está reservado: es el segmento literal de la ruta GET /roles/details
     */
    @Test
    void reservedName_isDetails() {
        assertEquals("DETAILS", RoleNames.RESERVED);
        assertTrue(RoleNames.isReserved("DETAILS"));
    }

    /**
     * Solo el nombre guardado exacto está reservado: otros nombres, minúsculas, prefijos y null no lo están
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ADMIN", "DETAIL", "DETAILS_X", "XDETAILS", "details", "Details", " DETAILS"})
    void isReserved_otherNames_areNotReserved(String name) {
        assertFalse(RoleNames.isReserved(name));
    }

    /**
     * Un nombre recibido en minúsculas se normaliza a DETAILS y entonces queda reservado
     */
    @Test
    void normalize_detailsInAnyCase_becomesTheReservedName() {
        assertTrue(RoleNames.isReserved(RoleNames.normalize("details")));
        assertTrue(RoleNames.isReserved(RoleNames.normalize(" Details ")));
    }

    /**
     * Un nombre guardado son mayúsculas, dígitos y guion bajo, de 2 a 30 caracteres y empieza con una letra
     */
    @ParameterizedTest
    @ValueSource(strings = {"AB", "ADMIN", "MONITOR_EVACUACION", "ROL_2", "A_1", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234"})
    void hasStoredFormat_validNames_areAccepted(String name) {
        assertTrue(RoleNames.hasStoredFormat(name));
    }

    /**
     * Minúsculas, espacios, símbolos, inicio no alfabético y longitudes fuera de rango no son un nombre guardado
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "A", "admin", "Admin", " ADMIN", "ADMIN ", "1ADMIN", "_ADMIN", "AD-MIN", "AD MIN",
            "ADMINÑ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345"})
    void hasStoredFormat_invalidNames_areRejected(String name) {
        assertFalse(RoleNames.hasStoredFormat(name));
    }

    /**
     * Un nombre recibido se recorta y se pasa a mayúsculas
     */
    @Test
    void normalize_trimsAndUppercases() {
        assertEquals("MONITOR_EVACUACION", RoleNames.normalize("  monitor_evacuacion "));
        assertEquals("ADMIN", RoleNames.normalize("Admin"));
        assertEquals("ROL_2", RoleNames.normalize("rol_2"));
    }

    /**
     * Un nombre recibido con formato inválido da null
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "a", "1rol", "_rol", "rol-nuevo", "rol nuevo", "ñandú",
            "abcdefghijklmnopqrstuvwxyz12345"})
    void normalize_invalidNames_returnNull(String raw) {
        assertNull(RoleNames.normalize(raw));
    }

    /**
     * Los roles se ordenan alfabéticamente sin importar el orden del conjunto recibido
     */
    @Test
    void sorted_isAlphabetical() {
        Set<String> received = new HashSet<>(List.of("PROFESOR", "ADMIN", "ESTUDIANTE", "BRIGADISTA"));

        assertEquals(List.of("ADMIN", "BRIGADISTA", "ESTUDIANTE", "PROFESOR"), RoleNames.sorted(received));
    }

    /**
     * Un conjunto vacío da una lista vacía
     */
    @Test
    void sorted_empty_returnsAnEmptyList() {
        assertTrue(RoleNames.sorted(Set.of()).isEmpty());
    }

    /**
     * No modifica el conjunto recibido
     */
    @Test
    void sorted_doesNotModifyTheReceivedSet() {
        Set<String> received = new HashSet<>(List.of("PROFESOR", "ADMIN"));

        RoleNames.sorted(received);

        assertEquals(Set.of("PROFESOR", "ADMIN"), received);
    }

    /**
     * La lista devuelta es de solo lectura
     */
    @Test
    void sorted_resultIsReadOnly() {
        List<String> sorted = RoleNames.sorted(Set.of("ADMIN"));

        assertThrows(UnsupportedOperationException.class, () -> sorted.add("PROFESOR"));
    }
}
