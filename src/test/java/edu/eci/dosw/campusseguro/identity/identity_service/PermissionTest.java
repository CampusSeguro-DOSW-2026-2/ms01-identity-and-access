package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;

/**
 * Catálogo de permisos (RN-MS01-14): 48 códigos con el formato modulo:recurso:accion, sin repetidos y con
 * descripción
 */
class PermissionTest {

    /**
     * El catálogo tiene 48 permisos
     */
    @Test
    void catalog_hasFortyEightPermissions() {
        assertEquals(48, Permission.values().length);
    }

    /**
     * Todos los códigos tienen el formato modulo:recurso:accion y ninguno se repite
     */
    @Test
    void everyCode_hasTheFormatAndIsUnique() {
        Set<String> codes = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertTrue(PermissionCodes.hasFormat(permission.getCode()), permission.getCode());
            assertTrue(codes.add(permission.getCode()), permission.getCode());
        }
    }

    /**
     * Todos los permisos tienen una descripción con contenido
     */
    @Test
    void everyPermission_hasADescription() {
        for (Permission permission : Permission.values()) {
            assertFalse(permission.getDescription().isBlank(), permission.getCode());
        }
    }

    /**
     * El catálogo tiene permisos de los ocho módulos
     */
    @Test
    void catalog_coversTheEightModules() {
        Set<String> modules = Arrays.stream(Permission.values())
                .map(permission -> permission.getCode().substring(0, 4))
                .collect(Collectors.toSet());

        assertEquals(Set.of("ms01", "ms02", "ms03", "ms04", "ms05", "ms06", "ms07", "ms08"), modules);
    }

    /**
     * Un código del catálogo se encuentra tal cual
     */
    @Test
    void fromCode_knownCode_returnsThePermission() {
        assertEquals(Permission.MS01_ROLE_MANAGE, Permission.fromCode("ms01:role:manage").orElseThrow());
        assertEquals(Permission.MS08_AUDIT_READ, Permission.fromCode("ms08:audit:read").orElseThrow());
    }

    /**
     * La búsqueda distingue mayúsculas y no recorta espacios
     *
     * @param code código que no está en el catálogo
     */
    @ParameterizedTest
    @ValueSource(strings = {"MS01:ROLE:MANAGE", "Ms01:role:manage", " ms01:role:manage", "ms01:role:manage ",
            "ms99:x:y", "", "ms01:role"})
    void fromCode_unknownCode_isEmpty(String code) {
        assertTrue(Permission.fromCode(code).isEmpty());
    }

    /**
     * Un código nulo no está en el catálogo
     */
    @Test
    void fromCode_null_isEmpty() {
        assertTrue(Permission.fromCode(null).isEmpty());
    }

    /**
     * El catálogo ordenado por código está completo y en orden alfabético
     */
    @Test
    void sortedByCode_returnsTheWholeCatalogInAlphabeticalOrder() {
        List<String> codes = Permission.sortedByCode().stream().map(Permission::getCode).toList();

        assertEquals(48, codes.size());
        assertEquals(codes.stream().sorted().toList(), codes);
        assertEquals("ms01:password:change", codes.get(0));
        assertEquals("ms08:indicators:read", codes.get(codes.size() - 1));
    }

    /**
     * El permiso de gestión de roles y el de consulta completa existen con el código del contrato
     */
    @Test
    void roleManagementPermissions_haveTheContractCodes() {
        assertEquals("ms01:role:manage", Permission.MS01_ROLE_MANAGE.getCode());
        assertEquals("ms01:role:read_all", Permission.MS01_ROLE_READ_ALL.getCode());
        assertEquals("ms01:role:read_own", Permission.MS01_ROLE_READ_OWN.getCode());
        assertEquals("ms01:user:roles", Permission.MS01_USER_ROLES.getCode());
    }
}
