package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;

/**
 * Formato de los códigos de permiso y cálculo de los permisos de un rol y de un usuario a partir de los
 * roles guardados
 */
class PermissionCodesTest {

    /**
     * Rol guardado con los permisos indicados
     *
     * @param name nombre del rol
     * @param permissions permisos guardados
     * @return la entidad
     */
    private RoleEntity role(String name, String... permissions) {
        return TestEntities.role(name, permissions);
    }

    /**
     * Códigos con el formato modulo:recurso:accion
     *
     * @param code código válido
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms01:role:manage", "ms04:checkin:read_own", "ms99:a:b", "ms00:x1:y2"})
    void hasFormat_validCodes_areAccepted(String code) {
        assertTrue(PermissionCodes.hasFormat(code));
    }

    /**
     * Códigos que no tienen el formato
     *
     * @param code código inválido
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "ms1:role:manage", "MS01:role:manage", "ms01:Role:manage", "ms01:role",
            "ms01:role:manage:x", "ms01:1role:manage", "ms01:role:_manage", " ms01:role:manage", "ms01:role-x:y",
            "ms01::manage"})
    void hasFormat_invalidCodes_areRejected(String code) {
        assertFalse(PermissionCodes.hasFormat(code));
    }

    /**
     * Un código nulo no tiene el formato
     */
    @Test
    void hasFormat_null_isRejected() {
        assertFalse(PermissionCodes.hasFormat(null));
    }

    /**
     * Los permisos de un rol salen ordenados y sin repetidos
     */
    @Test
    void ofRole_sortsAndDeduplicates() {
        RoleEntity role = role("MONITOR", "ms04:checkin:write", "ms01:profile:read", "ms04:checkin:write");

        assertEquals(List.of("ms01:profile:read", "ms04:checkin:write"), PermissionCodes.ofRole(role));
    }

    /**
     * Los permisos del rol se devuelven tal como están en la base, sin cambiar mayúsculas
     */
    @Test
    void ofRole_returnsTheStoredCodesAsTheyAre() {
        RoleEntity role = role("MIXTO", "ms01:profile:read", "ms99:x:y");

        assertEquals(List.of("ms01:profile:read", "ms99:x:y"), PermissionCodes.ofRole(role));
    }

    /**
     * Un rol sin permisos devuelve una lista vacía
     */
    @Test
    void ofRole_withoutPermissions_isEmpty() {
        assertTrue(PermissionCodes.ofRole(role("VACIO")).isEmpty());
    }

    /**
     * Los permisos efectivos son la unión de los de todos los roles, ordenada y sin repetidos
     */
    @Test
    void effective_isTheUnionOfTheRolesPermissions() {
        List<String> effective = PermissionCodes.effective(List.of(
                role("A", "ms04:checkin:write", "ms01:profile:read"),
                role("B", "ms01:profile:read", "ms03:alert:receive")));

        assertEquals(List.of("ms01:profile:read", "ms03:alert:receive", "ms04:checkin:write"), effective);
    }

    /**
     * Sin roles no hay permisos efectivos
     */
    @Test
    void effective_withoutRoles_isEmpty() {
        assertTrue(PermissionCodes.effective(List.of()).isEmpty());
    }

    /**
     * Un rol repetido entre los roles del usuario no repite sus permisos
     */
    @Test
    void effective_sameRoleTwice_doesNotRepeatPermissions() {
        RoleEntity role = role("A", "ms01:profile:read", "ms04:checkin:write");

        assertEquals(List.of("ms01:profile:read", "ms04:checkin:write"),
                PermissionCodes.effective(List.of(role, role)));
    }
}
