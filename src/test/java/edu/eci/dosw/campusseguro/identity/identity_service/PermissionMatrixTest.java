package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * RF-MS01-05 / RN-MS01-14: la matriz Rol-Permiso definida en el codigo (enums UserRole y Permission)
 * coincide fila por fila con 3_matriz-roles-permisos.md, y los permisos de un usuario con varios roles
 * son la union de los de cada rol, sin herencia (MR-01).
 */
class PermissionMatrixTest {

    private static final String EVERY_ROLE =
            "ESTUDIANTE,PROFESOR,ADMINISTRATIVO,BRIGADISTA,COORDINADOR,ORGANIZADOR,ADMIN,AUDITOR";

    private static final String COMMUNITY =
            "ESTUDIANTE,PROFESOR,ADMINISTRATIVO,BRIGADISTA,COORDINADOR,ORGANIZADOR";

    private static final Pattern CODE_FORMAT = Pattern.compile("^ms0[1-8]:[a-z_]+:[a-z_]+$");

    /**
     * Matriz esperada, transcrita de 3_matriz-roles-permisos.md: codigo del permiso y roles que lo tienen.
     */
    private static final String[][] EXPECTED_MATRIX = {
            {"ms01:profile:read", EVERY_ROLE},
            {"ms01:profile:write", EVERY_ROLE},
            {"ms01:password:change", EVERY_ROLE},
            {"ms01:session:logout", EVERY_ROLE},
            {"ms01:role:read_own", EVERY_ROLE},
            {"ms01:role:read_all", "ADMIN"},
            {"ms01:user:read", "ADMIN,AUDITOR"},
            {"ms01:user:status", "ADMIN"},
            {"ms01:user:roles", "ADMIN"},
            {"ms02:campus:read", EVERY_ROLE},
            {"ms02:route:read", EVERY_ROLE},
            {"ms02:preventive_info:read", EVERY_ROLE},
            {"ms02:building:write", "ADMIN"},
            {"ms02:space:write", "ADMIN"},
            {"ms02:safe_zone:write", "ADMIN"},
            {"ms02:route:write", "ADMIN"},
            {"ms02:route_status:write", "COORDINADOR,ADMIN"},
            {"ms02:qr:manage", "ADMIN"},
            {"ms03:alert:receive", COMMUNITY},
            {"ms03:alert:manage", "COORDINADOR"},
            {"ms03:alert:manage_drill", "ORGANIZADOR"},
            {"ms03:alert:read_history", "COORDINADOR,ORGANIZADOR,AUDITOR"},
            {"ms04:checkin:write", COMMUNITY},
            {"ms04:checkin:read_own", COMMUNITY},
            {"ms04:assistance:request", COMMUNITY},
            {"ms04:dashboard:read", "BRIGADISTA,COORDINADOR,ORGANIZADOR,AUDITOR"},
            {"ms04:history:read", "COORDINADOR,ORGANIZADOR,AUDITOR"},
            {"ms05:brigadier:assign", "COORDINADOR"},
            {"ms05:panel:read_own", "BRIGADISTA"},
            {"ms05:assistance:read", "BRIGADISTA,COORDINADOR"},
            {"ms05:assistance:register", "BRIGADISTA,COORDINADOR"},
            {"ms05:assistance:assign", "COORDINADOR"},
            {"ms05:assistance:update_status", "BRIGADISTA,COORDINADOR"},
            {"ms05:incident:report", "PROFESOR,BRIGADISTA,COORDINADOR"},
            {"ms05:incident:read", "BRIGADISTA,COORDINADOR,AUDITOR"},
            {"ms05:incident:update", "BRIGADISTA,COORDINADOR"},
            {"ms06:drill:manage", "ORGANIZADOR"},
            {"ms06:drill:execute", "ORGANIZADOR"},
            {"ms06:drill:evaluate", "ORGANIZADOR"},
            {"ms06:drill:read", "COORDINADOR,ORGANIZADOR,AUDITOR"},
            {"ms06:drill:compare_report", "COORDINADOR,ORGANIZADOR,AUDITOR"},
            {"ms07:content:read", COMMUNITY},
            {"ms07:quiz:take", COMMUNITY},
            {"ms07:tutor:use", COMMUNITY},
            {"ms07:content:manage", "ADMIN"},
            {"ms08:indicators:read", "COORDINADOR,ORGANIZADOR,AUDITOR"},
            {"ms08:audit:read", "ADMIN,AUDITOR"},
    };

    /**
     * Convierte una lista de nombres de rol separados por coma en un conjunto de roles.
     *
     * @param names nombres de rol separados por coma
     * @return conjunto de roles con esos nombres
     */
    private static Set<UserRole> rolesOf(String names) {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String name : names.split(",")) {
            roles.add(UserRole.valueOf(name));
        }
        return roles;
    }

    /**
     * Calcula los roles que tienen un permiso segun el codigo.
     *
     * @param permission permiso consultado
     * @return conjunto de roles cuyo conjunto de permisos incluye el permiso
     */
    private static Set<UserRole> holdersOf(Permission permission) {
        Set<UserRole> holders = EnumSet.noneOf(UserRole.class);
        for (UserRole role : UserRole.values()) {
            if (role.hasPermission(permission)) {
                holders.add(role);
            }
        }
        return holders;
    }

    /**
     * Busca el permiso del enum que tiene un codigo.
     *
     * @param code codigo con el formato ms:recurso:accion
     * @return el permiso, o null si ninguno tiene ese codigo
     */
    private static Permission permissionWithCode(String code) {
        for (Permission permission : Permission.values()) {
            if (permission.getCode().equals(code)) {
                return permission;
            }
        }
        return null;
    }

    /**
     * El catalogo de roles tiene exactamente los ocho roles de RN-MS01-14.
     */
    @Test
    void catalog_hasTheEightRolesOfRnMs0114() {
        Set<String> names = new HashSet<>();
        for (UserRole role : UserRole.values()) {
            names.add(role.name());
        }

        assertEquals(Set.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA", "COORDINADOR",
                "ORGANIZADOR", "ADMIN", "AUDITOR"), names);
    }

    /**
     * El enum Permission tiene tantos permisos como filas la matriz y cada fila esperada existe.
     */
    @Test
    void permissions_matchTheRowsOfTheMatrix() {
        assertEquals(EXPECTED_MATRIX.length, Permission.values().length);
        for (String[] row : EXPECTED_MATRIX) {
            assertNotNull(permissionWithCode(row[0]), "Falta el permiso " + row[0]);
        }
    }

    /**
     * Los codigos de permiso son unicos y respetan el formato ms:recurso:accion.
     */
    @Test
    void permissionCodes_areUniqueAndWellFormed() {
        Set<String> codes = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertTrue(CODE_FORMAT.matcher(permission.getCode()).matches(),
                    "Codigo con formato invalido: " + permission.getCode());
            assertTrue(codes.add(permission.getCode()), "Codigo repetido: " + permission.getCode());
            assertNotNull(permission.getDescription());
            assertFalse(permission.getDescription().isBlank());
        }
    }

    /**
     * Cada fila de la matriz la tienen exactamente los roles indicados, ni uno mas ni uno menos.
     */
    @Test
    void everyRowOfTheMatrix_isGrantedToExactlyTheExpectedRoles() {
        for (String[] row : EXPECTED_MATRIX) {
            Permission permission = permissionWithCode(row[0]);

            assertEquals(rolesOf(row[1]), holdersOf(permission), "Roles del permiso " + row[0]);
        }
    }

    /**
     * Solo el ADMIN gestiona roles, estados y el catalogo completo de roles.
     */
    @Test
    void roleAdministrationPermissions_belongOnlyToAdmin() {
        assertEquals(EnumSet.of(UserRole.ADMIN), holdersOf(Permission.MS01_USER_ROLES));
        assertEquals(EnumSet.of(UserRole.ADMIN), holdersOf(Permission.MS01_USER_STATUS));
        assertEquals(EnumSet.of(UserRole.ADMIN), holdersOf(Permission.MS01_ROLE_READ_ALL));
    }

    /**
     * El AUDITOR consulta usuarios pero no cambia roles ni ve el catalogo completo de roles.
     */
    @Test
    void auditor_readsUsersButCannotManageRoles() {
        assertTrue(UserRole.AUDITOR.hasPermission(Permission.MS01_USER_READ));
        assertFalse(UserRole.AUDITOR.hasPermission(Permission.MS01_USER_ROLES));
        assertFalse(UserRole.AUDITOR.hasPermission(Permission.MS01_ROLE_READ_ALL));
    }

    /**
     * Todos los roles pueden consultar sus propios roles y permisos (ms01:role:read_own).
     */
    @Test
    void everyRole_canReadItsOwnRoles() {
        for (UserRole role : UserRole.values()) {
            assertTrue(role.hasPermission(Permission.MS01_ROLE_READ_OWN), role.name());
        }
    }

    /**
     * El conjunto de permisos de un rol no se puede modificar desde fuera (RN-MS01-14: no se edita en
     * tiempo de ejecucion).
     */
    @Test
    void rolePermissions_areImmutable() {
        Set<Permission> permissions = UserRole.ESTUDIANTE.getPermissions();

        assertThrows(UnsupportedOperationException.class,
                () -> permissions.add(Permission.MS01_USER_ROLES));
        assertFalse(UserRole.ESTUDIANTE.hasPermission(Permission.MS01_USER_ROLES));
    }

    /**
     * getPermissionCodes entrega un codigo por permiso, en el mismo orden del conjunto de permisos.
     */
    @Test
    void permissionCodes_listsOneCodePerPermission() {
        List<String> expected = new ArrayList<>();
        for (Permission permission : UserRole.BRIGADISTA.getPermissions()) {
            expected.add(permission.getCode());
        }

        assertEquals(expected, UserRole.BRIGADISTA.getPermissionCodes());
        assertTrue(UserRole.BRIGADISTA.getPermissionCodes().contains("ms05:panel:read_own"));
    }

    /**
     * MR-01: un usuario con varios roles tiene la union de los permisos de cada uno.
     */
    @Test
    void permissionsOf_severalRoles_isTheUnion() {
        Set<Permission> union = UserRole.permissionsOf(List.of(UserRole.PROFESOR, UserRole.AUDITOR));

        Set<Permission> expected = EnumSet.noneOf(Permission.class);
        expected.addAll(UserRole.PROFESOR.getPermissions());
        expected.addAll(UserRole.AUDITOR.getPermissions());
        assertEquals(expected, union);
        assertTrue(union.contains(Permission.MS05_INCIDENT_REPORT));
        assertTrue(union.contains(Permission.MS08_AUDIT_READ));
    }

    /**
     * MR-01: ningun rol hereda de otro; ADMIN no recibe los permisos operativos de los demas roles.
     */
    @Test
    void permissionsOf_adminDoesNotInheritOperationalPermissions() {
        Set<Permission> adminPermissions = UserRole.permissionsOf(List.of(UserRole.ADMIN));

        assertFalse(adminPermissions.contains(Permission.MS05_INCIDENT_REPORT));
        assertFalse(adminPermissions.contains(Permission.MS06_DRILL_MANAGE));
        assertFalse(adminPermissions.contains(Permission.MS04_CHECKIN_WRITE));
    }

    /**
     * Sin roles no hay permisos (denegado por defecto).
     */
    @Test
    void permissionsOf_noRoles_isEmpty() {
        assertTrue(UserRole.permissionsOf(List.of()).isEmpty());
    }

    /**
     * Los permisos comunes a todos los roles son los de perfil, sesion, consulta de roles y contenido
     * publico del campus.
     */
    @Test
    void grantedToEveryRole_hasTheEightCommonPermissions() {
        Set<Permission> common = Permission.grantedToEveryRole();

        assertEquals(8, common.size());
        assertTrue(common.contains(Permission.MS01_PROFILE_READ));
        assertTrue(common.contains(Permission.MS01_SESSION_LOGOUT));
        assertTrue(common.contains(Permission.MS02_CAMPUS_READ));
    }
}
