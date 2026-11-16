package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;

/**
 * Permisos de MS01 que define el código (RN-MS01-14): 10 códigos con el formato modulo:recurso:accion, sin
 * repetidos y con descripción
 */
class PermissionTest {

    /**
     * El código define solo los 10 permisos de MS01
     */
    @Test
    void enum_hasTheTenMs01Permissions() {
        assertEquals(10, Permission.values().length);
    }

    /**
     * Todos los códigos tienen el formato modulo:recurso:accion, ninguno se repite y todos son de MS01
     */
    @Test
    void everyCode_hasTheFormatIsUniqueAndBelongsToMs01() {
        Set<String> codes = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertTrue(PermissionCodes.hasFormat(permission.getCode()), permission.getCode());
            assertTrue(permission.getCode().startsWith(Permission.PREFIX), permission.getCode());
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
     * El prefijo del enum es el del módulo de MS01
     */
    @Test
    void prefix_isTheMs01Module() {
        assertEquals("ms01:", Permission.PREFIX);
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

    /**
     * Los permisos del perfil existen con el código del contrato
     */
    @Test
    void profilePermissions_haveTheContractCodes() {
        assertEquals("ms01:profile:read", Permission.MS01_PROFILE_READ.getCode());
        assertEquals("ms01:profile:write", Permission.MS01_PROFILE_WRITE.getCode());
    }
}
