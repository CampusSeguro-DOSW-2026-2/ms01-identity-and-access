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
 * RF-MS01-05 / RN-MS01-14: los scripts de base de datos siembran el catálogo de 48 permisos y la asignación
 * inicial de permisos a los ocho roles tal como los define la Matriz Rol-Permiso, sin herencia entre roles
 * (MR-01), y el código de MS01 solo declara permisos que existen en esa siembra
 */
class PermissionMatrixTest {

    private static final Pattern CODE_FORMAT = Pattern.compile("^ms0[1-8]:[a-z_]+:[a-z_]+$");

    private static final Pattern PERMISSIONS_BLOCK = Pattern.compile(
            "INSERT INTO permisos \\(nombre, descripcion\\) VALUES(.*?);", Pattern.DOTALL);

    private static final Pattern PERMISSION_ROW = Pattern.compile("\\('([^']+)',\\s*'((?:[^']|'')*)'\\)");

    private static final Pattern ROLES_BLOCK = Pattern.compile(
            "INSERT INTO roles \\(nombre, descripcion, es_sistema\\) VALUES(.*?);", Pattern.DOTALL);

    private static final Pattern ROLE_ROW = Pattern.compile(
            "\\('([A-Z_]+)',\\s*'(?:[^']|'')*',\\s*(TRUE|FALSE)\\)");

    private static final Pattern ASSIGNMENT_ROW = Pattern.compile("\\('([A-Z_]+)',\\s*ARRAY\\[(.*?)\\]\\)",
            Pattern.DOTALL);

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private static final Set<String> SEEDED_ROLES = Set.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO",
            "BRIGADISTA", "COORDINADOR", "ORGANIZADOR", "ADMIN", "AUDITOR");

    private static final List<String> SEED_SCRIPTS = List.of("database/001_create_schema.sql",
            "database/migraciones/004_migracion_roles_permisos.sql");

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
     * Lee de un script SQL el bloque que siembra la tabla permisos
     *
     * @param script ruta del script, relativa a la raíz del proyecto
     * @return código de cada permiso y su descripción, ordenados por código
     * @throws IOException si el script no se puede leer
     */
    private static Map<String, String> permissionsOf(String script) throws IOException {
        String sql = Files.readString(Path.of(script), StandardCharsets.UTF_8);
        Matcher block = PERMISSIONS_BLOCK.matcher(sql);
        assertTrue(block.find(), "Falta la siembra de permisos en " + script);
        Map<String, String> seed = new TreeMap<>();
        Matcher row = PERMISSION_ROW.matcher(block.group(1));
        while (row.find()) {
            seed.put(row.group(1), row.group(2).replace("''", "'"));
        }
        return seed;
    }

    /**
     * Lee de un script SQL la marca de sistema de cada rol sembrado
     *
     * @param script ruta del script, relativa a la raíz del proyecto
     * @return nombre del rol y su marca es_sistema (TRUE o FALSE)
     * @throws IOException si el script no se puede leer
     */
    private static Map<String, String> rolesOf(String script) throws IOException {
        String sql = Files.readString(Path.of(script), StandardCharsets.UTF_8);
        Matcher block = ROLES_BLOCK.matcher(sql);
        assertTrue(block.find(), "Falta la siembra de roles en " + script);
        Map<String, String> seed = new TreeMap<>();
        Matcher row = ROLE_ROW.matcher(block.group(1));
        while (row.find()) {
            seed.put(row.group(1), row.group(2));
        }
        return seed;
    }

    /**
     * Lee de un script SQL la asignación inicial de permisos a cada rol
     *
     * @param script ruta del script, relativa a la raíz del proyecto
     * @return nombre del rol y los códigos de sus permisos en el orden del script
     * @throws IOException si el script no se puede leer
     */
    private static Map<String, List<String>> assignmentsOf(String script) throws IOException {
        String sql = Files.readString(Path.of(script), StandardCharsets.UTF_8);
        int start = sql.indexOf("INSERT INTO rol_permiso");
        assertTrue(start >= 0, "Falta la asignación de permisos en " + script);
        Map<String, List<String>> seed = new TreeMap<>();
        Matcher row = ASSIGNMENT_ROW.matcher(sql.substring(start));
        while (row.find()) {
            List<String> codes = new ArrayList<>();
            Matcher code = QUOTED.matcher(row.group(2));
            while (code.find()) {
                codes.add(code.group(1));
            }
            seed.put(row.group(1), codes);
        }
        return seed;
    }

    /**
     * La siembra de permisos de cada script tiene 48 códigos con formato modulo:recurso:accion, sin repetir,
     * con descripción de hasta 200 caracteres, y es exactamente la unión de los permisos de la matriz
     *
     * @param script ruta del script
     * @throws IOException si el script no se puede leer
     */
    @ParameterizedTest
    @ValueSource(strings = {"database/001_create_schema.sql", "database/migraciones/004_migracion_roles_permisos.sql"})
    void seedPermissions_are48WellFormedAndEqualToTheMatrixUnion(String script) throws IOException {
        Map<String, String> seeded = permissionsOf(script);

        assertEquals(48, seeded.size());
        for (Map.Entry<String, String> permission : seeded.entrySet()) {
            assertTrue(CODE_FORMAT.matcher(permission.getKey()).matches(),
                    "Código con formato inválido: " + permission.getKey());
            assertFalse(permission.getValue().isBlank(), permission.getKey());
            assertTrue(permission.getValue().length() <= 200, permission.getKey());
        }
        Set<String> union = new TreeSet<>();
        for (List<String> codes : SeedRoles.PERMISSIONS.values()) {
            union.addAll(codes);
        }
        assertEquals(union, seeded.keySet());
    }

    /**
     * Los permisos que declara el enum de MS01 existen en la siembra con la misma descripción, y el enum no
     * declara permisos de otros módulos
     *
     * @throws IOException si el script no se puede leer
     */
    @Test
    void enum_declaresOnlyMs01PermissionsPresentInTheSeedWithTheSameDescription() throws IOException {
        Map<String, String> seeded = permissionsOf("database/001_create_schema.sql");
        Set<String> ms01InSeed = new TreeSet<>();
        for (String code : seeded.keySet()) {
            if (code.startsWith(Permission.PREFIX)) {
                ms01InSeed.add(code);
            }
        }
        Set<String> declared = new TreeSet<>();
        for (Permission permission : Permission.values()) {
            declared.add(permission.getCode());
            assertNotNull(permission.getDescription());
            assertEquals(seeded.get(permission.getCode()), permission.getDescription(), permission.getCode());
        }

        assertEquals(ms01InSeed, declared);
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
     * Cantidad de permisos que siembra cada rol según la Matriz Rol-Permiso, 172 asignaciones en total
     */
    @Test
    void seedRoles_haveTheExpectedNumberOfPermissions() {
        assertEquals(16, SeedRoles.PERMISSIONS.get("ESTUDIANTE").size());
        assertEquals(16, SeedRoles.PERMISSIONS.get("PROFESOR").size());
        assertEquals(16, SeedRoles.PERMISSIONS.get("ADMINISTRATIVO").size());
        assertEquals(23, SeedRoles.PERMISSIONS.get("BRIGADISTA").size());
        assertEquals(34, SeedRoles.PERMISSIONS.get("COORDINADOR").size());
        assertEquals(28, SeedRoles.PERMISSIONS.get("ORGANIZADOR").size());
        assertEquals(22, SeedRoles.PERMISSIONS.get("ADMIN").size());
        assertEquals(17, SeedRoles.PERMISSIONS.get("AUDITOR").size());
        int total = 0;
        for (List<String> codes : SeedRoles.PERMISSIONS.values()) {
            total += codes.size();
        }
        assertEquals(172, total);
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
        for (List<String> codes : SeedRoles.PERMISSIONS.values()) {
            for (String code : codes) {
                if (holdersOf(code).equals(SEEDED_ROLES)) {
                    common.add(code);
                }
            }
        }

        assertEquals(Set.of("ms01:password:change", "ms01:profile:read", "ms01:profile:write",
                "ms01:role:read_own", "ms01:session:logout", "ms02:campus:read", "ms02:preventive_info:read",
                "ms02:route:read"), common);
    }

    /**
     * MR-01: ningún rol hereda de otro, ADMIN no recibe los permisos operativos de simulacros ni de check-in
     */
    @Test
    void adminDoesNotInheritOperationalPermissions() {
        List<String> admin = SeedRoles.PERMISSIONS.get("ADMIN");

        assertFalse(admin.contains("ms06:drill:manage"));
        assertFalse(admin.contains("ms04:checkin:write"));
        assertFalse(admin.contains("ms03:alert:manage"));
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
        assertTrue(union.contains("ms05:incident:report"));
        assertTrue(union.contains("ms08:audit:read"));
    }

    /**
     * Sin roles no hay permisos (denegado por defecto)
     */
    @Test
    void permissionsOfNoRoles_isEmpty() {
        assertTrue(TestIdentities.permissionsOf().isEmpty());
    }

    /**
     * Cada script siembra los ocho roles con los mismos permisos de la matriz y marca como de sistema solo a
     * ESTUDIANTE y ADMIN
     *
     * @param script ruta del script
     * @throws IOException si el script no se puede leer
     */
    @ParameterizedTest
    @ValueSource(strings = {"database/001_create_schema.sql", "database/migraciones/004_migracion_roles_permisos.sql"})
    void seedScripts_seedTheMatrixAndMarkOnlyTwoSystemRoles(String script) throws IOException {
        Map<String, String> roles = rolesOf(script);
        Map<String, List<String>> assignments = assignmentsOf(script);

        assertEquals(SEEDED_ROLES, roles.keySet());
        assertEquals(SEEDED_ROLES, assignments.keySet());
        for (String role : SEEDED_ROLES) {
            assertEquals(SeedRoles.PERMISSIONS.get(role), assignments.get(role), "Permisos de " + role);
            boolean expectedSystem = role.equals("ESTUDIANTE") || role.equals("ADMIN");
            assertEquals(String.valueOf(expectedSystem).toUpperCase(), roles.get(role),
                    "es_sistema de " + role);
        }
    }

    /**
     * El script de creación y la migración siembran exactamente lo mismo
     *
     * @throws IOException si algún script no se puede leer
     */
    @Test
    void script001AndMigration004_seedTheSameData() throws IOException {
        assertEquals(permissionsOf(SEED_SCRIPTS.get(0)), permissionsOf(SEED_SCRIPTS.get(1)));
        assertEquals(rolesOf(SEED_SCRIPTS.get(0)), rolesOf(SEED_SCRIPTS.get(1)));
        assertEquals(assignmentsOf(SEED_SCRIPTS.get(0)), assignmentsOf(SEED_SCRIPTS.get(1)));
    }
}
