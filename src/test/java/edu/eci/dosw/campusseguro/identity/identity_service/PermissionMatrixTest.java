package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;

/**
 * RF-MS01-05 / RN-MS01-14: el catálogo de permisos del código y la asignación inicial de permisos a los ocho
 * roles que siembran los scripts de base de datos coinciden con la Matriz Rol-Permiso, sin herencia entre roles
 * (MR-01)
 */
class PermissionMatrixTest {

    private static final Pattern CODE_FORMAT = Pattern.compile("^ms0[1-8]:[a-z_]+:[a-z_]+$");

    private static final Pattern SEED_ROW = Pattern.compile(
            "\\('([A-Z_]+)',\\s*'[^']*',\\s*ARRAY\\[(.*?)\\],\\s*(TRUE|FALSE)\\)", Pattern.DOTALL);

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private static final Set<String> SEEDED_ROLES = Set.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO",
            "BRIGADISTA", "COORDINADOR", "ORGANIZADOR", "ADMIN", "AUDITOR");

    /**
     * Calcula los roles sembrados que tienen un permiso
     *
     * @param code código del permiso consultado
     * @return nombres de los roles sembrados cuyo conjunto incluye el permiso
     */
    private static Set<String> holdersOf(String code) {
        Set<String> holders = new TreeSet<>();
        for (Map.Entry<String, List<String>> role : SeedRoles.PERMISSIONS.entrySet()) {
            if (role.getValue().contains(code)) {
                holders.add(role.getKey());
            }
        }
        return holders;
    }

    /**
     * Lee de un script SQL la siembra de la tabla roles
     *
     * @param script ruta del script, relativa a la raíz del proyecto
     * @return nombre del rol y sus permisos, más la marca de sistema como último elemento de la lista
     * @throws IOException si el script no se puede leer
     */
    private static Map<String, List<String>> seedOf(String script) throws IOException {
        String sql = Files.readString(Path.of(script), StandardCharsets.UTF_8);
        Map<String, List<String>> seed = new TreeMap<>();
        Matcher row = SEED_ROW.matcher(sql);
        while (row.find()) {
            List<String> values = new ArrayList<>();
            Matcher code = QUOTED.matcher(row.group(2));
            while (code.find()) {
                values.add(code.group(1));
            }
            values.add(row.group(3));
            seed.put(row.group(1), values);
        }
        return seed;
    }

    /**
     * El catálogo del código tiene 48 permisos, con código único, formato modulo:recurso:accion y descripción
     */
    @Test
    void catalog_has48UniqueWellFormedPermissions() {
        Set<String> codes = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertTrue(CODE_FORMAT.matcher(permission.getCode()).matches(),
                    "Código con formato inválido: " + permission.getCode());
            assertTrue(codes.add(permission.getCode()), "Código repetido: " + permission.getCode());
            assertNotNull(permission.getDescription());
            assertFalse(permission.getDescription().isBlank());
        }
        assertEquals(48, codes.size());
    }

    /**
     * Cada permiso del catálogo lo tiene al menos un rol sembrado y ningún rol sembrado tiene un permiso fuera
     * del catálogo
     */
    @Test
    void seedRoles_useExactlyTheCatalog() {
        Set<String> granted = new TreeSet<>();
        for (List<String> permissions : SeedRoles.PERMISSIONS.values()) {
            for (String code : permissions) {
                assertTrue(Permission.fromCode(code).isPresent(), "Permiso fuera del catálogo: " + code);
                granted.add(code);
            }
        }
        Set<String> catalog = new TreeSet<>();
        for (Permission permission : Permission.values()) {
            catalog.add(permission.getCode());
        }
        assertEquals(catalog, granted);
    }

    /**
     * Hay ocho roles sembrados, cada uno con permisos sin repetir y ordenados por código
     */
    @Test
    void seedRoles_areEightWithSortedUniquePermissions() {
        assertEquals(SEEDED_ROLES, SeedRoles.PERMISSIONS.keySet());
        for (Map.Entry<String, List<String>> role : SeedRoles.PERMISSIONS.entrySet()) {
            List<String> sorted = new ArrayList<>(new TreeSet<>(role.getValue()));
            assertEquals(sorted, role.getValue(), "Permisos de " + role.getKey());
        }
    }

    /**
     * Cantidad de permisos que siembra cada rol según la Matriz Rol-Permiso
     */
    @Test
    void seedRoles_haveTheExpectedNumberOfPermissions() {
        assertEquals(15, SeedRoles.PERMISSIONS.get("ESTUDIANTE").size());
        assertEquals(16, SeedRoles.PERMISSIONS.get("PROFESOR").size());
        assertEquals(15, SeedRoles.PERMISSIONS.get("ADMINISTRATIVO").size());
        assertEquals(23, SeedRoles.PERMISSIONS.get("BRIGADISTA").size());
        assertEquals(34, SeedRoles.PERMISSIONS.get("COORDINADOR").size());
        assertEquals(25, SeedRoles.PERMISSIONS.get("ORGANIZADOR").size());
        assertEquals(21, SeedRoles.PERMISSIONS.get("ADMIN").size());
        assertEquals(17, SeedRoles.PERMISSIONS.get("AUDITOR").size());
    }

    /**
     * Solo el ADMIN gestiona roles, estados y el catálogo completo de roles, y solo él puede tener
     * ms01:role:manage
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms01:role:manage", "ms01:role:read_all", "ms01:user:roles", "ms01:user:status"})
    void roleAdministrationPermissions_belongOnlyToAdmin(String code) {
        assertEquals(Set.of("ADMIN"), holdersOf(code));
    }

    /**
     * El AUDITOR consulta usuarios pero no cambia roles ni ve el catálogo completo de roles
     */
    @Test
    void auditor_readsUsersButCannotManageRoles() {
        assertEquals(Set.of("ADMIN", "AUDITOR"), holdersOf(Permission.MS01_USER_READ.getCode()));
        assertFalse(SeedRoles.PERMISSIONS.get("AUDITOR").contains(Permission.MS01_USER_ROLES.getCode()));
        assertFalse(SeedRoles.PERMISSIONS.get("AUDITOR").contains(Permission.MS01_ROLE_READ_ALL.getCode()));
    }

    /**
     * Todos los roles pueden consultar sus propios roles y permisos (ms01:role:read_own)
     */
    @Test
    void everyRole_canReadItsOwnRoles() {
        assertEquals(SEEDED_ROLES, holdersOf(Permission.MS01_ROLE_READ_OWN.getCode()));
    }

    /**
     * Los permisos comunes a todos los roles son los de perfil, sesión, consulta de roles y contenido público
     * del campus
     */
    @Test
    void grantedToEveryRole_areTheEightCommonPermissions() {
        Set<String> common = new TreeSet<>();
        for (Permission permission : Permission.values()) {
            if (holdersOf(permission.getCode()).equals(SEEDED_ROLES)) {
                common.add(permission.getCode());
            }
        }

        assertEquals(Set.of("ms01:password:change", "ms01:profile:read", "ms01:profile:write",
                "ms01:role:read_own", "ms01:session:logout", "ms02:campus:read", "ms02:preventive_info:read",
                "ms02:route:read"), common);
    }

    /**
     * MR-01: ningún rol hereda de otro, ADMIN no recibe los permisos operativos de los demás roles
     */
    @Test
    void adminDoesNotInheritOperationalPermissions() {
        List<String> admin = SeedRoles.PERMISSIONS.get("ADMIN");

        assertFalse(admin.contains(Permission.MS05_INCIDENT_REPORT.getCode()));
        assertFalse(admin.contains(Permission.MS06_DRILL_MANAGE.getCode()));
        assertFalse(admin.contains(Permission.MS04_CHECKIN_WRITE.getCode()));
    }

    /**
     * MR-01: un usuario con varios roles tiene la unión de los permisos de cada uno
     */
    @Test
    void permissionsOfSeveralRoles_isTheUnion() {
        Set<String> union = TestIdentities.permissionsOf("PROFESOR", "AUDITOR");

        Set<String> expected = new TreeSet<>(SeedRoles.PERMISSIONS.get("PROFESOR"));
        expected.addAll(SeedRoles.PERMISSIONS.get("AUDITOR"));
        assertEquals(expected, union);
        assertTrue(union.contains(Permission.MS05_INCIDENT_REPORT.getCode()));
        assertTrue(union.contains(Permission.MS08_AUDIT_READ.getCode()));
    }

    /**
     * Sin roles no hay permisos (denegado por defecto)
     */
    @Test
    void permissionsOfNoRoles_isEmpty() {
        assertTrue(TestIdentities.permissionsOf().isEmpty());
    }

    /**
     * El script 001 siembra los ocho roles con los mismos permisos de la matriz y marca como de sistema solo a
     * ESTUDIANTE y ADMIN
     *
     * @throws IOException si el script no se puede leer
     */
    @Test
    void script001_seedsTheMatrix() throws IOException {
        assertSeedMatchesTheMatrix(seedOf("database/001_create_schema.sql"));
    }

    /**
     * El script de migración 004 siembra los mismos ocho roles y permisos que el script 001
     *
     * @throws IOException si el script no se puede leer
     */
    @Test
    void script004_seedsTheMatrix() throws IOException {
        assertSeedMatchesTheMatrix(seedOf("database/migraciones/004_migracion_roles_permisos.sql"));
    }

    /**
     * Comprueba que una siembra leída de un script coincide con la matriz
     *
     * @param seed siembra leída del script, con la marca de sistema como último elemento de cada lista
     */
    private static void assertSeedMatchesTheMatrix(Map<String, List<String>> seed) {
        assertEquals(SEEDED_ROLES, seed.keySet());
        for (Map.Entry<String, List<String>> role : seed.entrySet()) {
            List<String> values = role.getValue();
            String system = values.get(values.size() - 1);
            List<String> permissions = values.subList(0, values.size() - 1);

            assertEquals(SeedRoles.PERMISSIONS.get(role.getKey()), permissions, "Permisos de " + role.getKey());
            boolean expectedSystem = role.getKey().equals("ESTUDIANTE") || role.getKey().equals("ADMIN");
            assertEquals(String.valueOf(expectedSystem).toUpperCase(), system, "es_sistema de " + role.getKey());
        }
    }
}
