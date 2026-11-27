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
 * RF-MS01-05 / RN-MS01-14: the database scripts seed the catalog of 58 permissions and the initial assignment of
 * permissions to the eight roles as defined by the Role-Permission Matrix, without inheritance between roles (MR-01),
 * and the MS01 code only declares permissions that exist in that seeding
 */
class PermissionMatrixTest {

    private static final Pattern CODE_FORMAT = Pattern.compile("^ms0[1-8]:[a-z_]+:[a-z_]+$");

    private static final Pattern PERMISSIONS_BLOCK = Pattern.compile(
            "INSERT INTO permissions \\(name, description\\) VALUES(.*?);", Pattern.DOTALL);

    private static final Pattern PERMISSION_ROW = Pattern.compile("\\('([^']+)',\\s*'((?:[^']|'')*)'\\)");

    private static final Pattern ROLES_BLOCK = Pattern.compile(
            "INSERT INTO roles \\(name, description, is_system\\) VALUES(.*?);", Pattern.DOTALL);

    private static final Pattern ROLE_ROW = Pattern.compile(
            "\\('([A-Z_]+)',\\s*'(?:[^']|'')*',\\s*(TRUE|FALSE)\\)");

    private static final Pattern ASSIGNMENT_ROW = Pattern.compile("\\('([A-Z_]+)',\\s*ARRAY\\[(.*?)\\]\\)",
            Pattern.DOTALL);

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private static final Set<String> SEEDED_ROLES = Set.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO",
            "BRIGADISTA", "COORDINADOR", "ORGANIZADOR", "ADMIN", "AUDITOR");

    private static final String SCRIPT_001 = "database/001_create_schema.sql";
    private static final String CHANGE_SCRIPT = "database/maintenance/cambio_permisos.sql";

    private static final Pattern DELETED_PERMISSIONS = Pattern.compile(
            "DELETE FROM permissions WHERE name IN \\((.*?)\\);", Pattern.DOTALL);

    private static final Pattern GIVEN_ASSIGNMENTS = Pattern.compile(
            "INSERT INTO role_permissions.*?FROM \\(VALUES(.*?)\\) AS cambio", Pattern.DOTALL);

    private static final Pattern TAKEN_ASSIGNMENTS = Pattern.compile(
            "DELETE FROM role_permissions.*?\\(VALUES(.*?)\\) AS cambio", Pattern.DOTALL);

    private static final Pattern PAIR = Pattern.compile("\\('([A-Z_]+)',\\s*'([^']+)'\\)");

        /**
     * Computes the seeded roles that have a permission
     *
     * @param code code of the queried permission
     * @return names of the seeded roles whose set includes the permission
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
     * Reads from an SQL script the block that seeds the permissions table
     *
     * @param script path of the script, relative to the project root
     * @return code of each permission and its description, sorted by code
     * @throws IOException if the script cannot be read
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
     * Reads from an SQL script the system flag of each seeded role
     *
     * @param script path of the script, relative to the project root
     * @return name of the role and its is_system flag (TRUE or FALSE)
     * @throws IOException if the script cannot be read
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
     * Reads from an SQL script the initial assignment of permissions to each role
     *
     * @param script path of the script, relative to the project root
     * @return name of the role and the codes of its permissions in the order of the script
     * @throws IOException if the script cannot be read
     */
    private static Map<String, List<String>> assignmentsOf(String script) throws IOException {
        String sql = Files.readString(Path.of(script), StandardCharsets.UTF_8);
        int start = sql.indexOf("INSERT INTO matrix_assignments");
        if (start < 0) {
            start = sql.indexOf("INSERT INTO role_permissions");
        }
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
     * The permission seeding of the creation script has 58 codes with the format module:resource:action, without repeating,
     * with a description of up to 200 characters, and is exactly the union of the permissions of the matrix
     *
     * @throws IOException if the script cannot be read
     */
    @Test
    void seedPermissions_areWellFormedAndEqualToTheMatrixUnion() throws IOException {
        Map<String, String> seeded = permissionsOf(SCRIPT_001);

        assertEquals(58, seeded.size());
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
     * The permissions declared by the MS01 enum exist in the seeding with the same description, and the enum does not
     * declare permissions of other modules
     *
     * @throws IOException if the script cannot be read
     */
    @Test
    void enum_declaresOnlyMs01PermissionsPresentInTheSeedWithTheSameDescription() throws IOException {
        Map<String, String> seeded = permissionsOf(SCRIPT_001);
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
     * There are eight seeded roles, each one with permissions without repeating and sorted by code
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
     * Number of permissions that each role seeds according to the Role-Permission Matrix, 198 assignments in total
     */
    @Test
    void seedRoles_haveTheExpectedNumberOfPermissions() {
        assertEquals(18, SeedRoles.PERMISSIONS.get("ESTUDIANTE").size());
        assertEquals(19, SeedRoles.PERMISSIONS.get("PROFESOR").size());
        assertEquals(18, SeedRoles.PERMISSIONS.get("ADMINISTRATIVO").size());
        assertEquals(25, SeedRoles.PERMISSIONS.get("BRIGADISTA").size());
        assertEquals(35, SeedRoles.PERMISSIONS.get("COORDINADOR").size());
        assertEquals(34, SeedRoles.PERMISSIONS.get("ORGANIZADOR").size());
        assertEquals(27, SeedRoles.PERMISSIONS.get("ADMIN").size());
        assertEquals(22, SeedRoles.PERMISSIONS.get("AUDITOR").size());
        int total = 0;
        for (List<String> codes : SeedRoles.PERMISSIONS.values()) {
            total += codes.size();
        }
        assertEquals(198, total);
    }

        /**
     * Only ADMIN manages roles, statuses and the complete catalog of roles, and only it can have ms01:role:manage
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms01:role:manage", "ms01:role:read_all", "ms01:user:roles", "ms01:user:status"})
    void roleAdministrationPermissions_belongOnlyToAdmin(String code) {
        assertEquals(Set.of("ADMIN"), holdersOf(code));
    }

        /**
     * The AUDITOR queries users but does not change roles or see the complete catalog of roles
     */
    @Test
    void auditor_readsUsersButCannotManageRoles() {
        assertEquals(Set.of("ADMIN", "AUDITOR"), holdersOf(Permission.MS01_USER_READ.getCode()));
        assertFalse(SeedRoles.PERMISSIONS.get("AUDITOR").contains(Permission.MS01_USER_ROLES.getCode()));
        assertFalse(SeedRoles.PERMISSIONS.get("AUDITOR").contains(Permission.MS01_ROLE_READ_ALL.getCode()));
    }

        /**
     * All the roles can query their own roles and permissions (ms01:role:read_own)
     */
    @Test
    void everyRole_canReadItsOwnRoles() {
        assertEquals(SEEDED_ROLES, holdersOf(Permission.MS01_ROLE_READ_OWN.getCode()));
    }

        /**
     * The permissions common to all the roles are those of profile, session, role query, public campus content, the
     * history of alerts and the own incidents
     */
    @Test
    void grantedToEveryRole_areTheCommonPermissionsOfTheMatrix() {
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
                "ms02:route:read", "ms03:alert:read_history", "ms05:incident:manage_own"), common);
    }

        /**
     * MR-01: no role inherits from another, ADMIN does not receive the operational permissions of drills or check-in
     */
    @Test
    void adminDoesNotInheritOperationalPermissions() {
        List<String> admin = SeedRoles.PERMISSIONS.get("ADMIN");

        assertFalse(admin.contains("ms06:drill:manage"));
        assertFalse(admin.contains("ms04:checkin:write"));
        assertFalse(admin.contains("ms03:alert:manage"));
    }

        /**
     * MR-01: a user with several roles has the union of the permissions of each one
     */
    @Test
    void permissionsOfSeveralRoles_isTheUnion() {
        Set<String> union = TestIdentities.permissionsOf("PROFESOR", "AUDITOR");

        Set<String> expected = new TreeSet<>(SeedRoles.PERMISSIONS.get("PROFESOR"));
        expected.addAll(SeedRoles.PERMISSIONS.get("AUDITOR"));
        assertEquals(expected, union);
        assertTrue(union.contains("ms05:incident:report"));
        assertTrue(union.contains("ms08:audit_drill:read"));
    }

        /**
     * Without roles there are no permissions (denied by default)
     */
    @Test
    void permissionsOfNoRoles_isEmpty() {
        assertTrue(TestIdentities.permissionsOf().isEmpty());
    }

        /**
     * The creation script seeds the eight roles with the same permissions of the matrix and marks as system roles
     * only ESTUDIANTE and ADMIN
     *
     * @throws IOException if the script cannot be read
     */
    @Test
    void seedScript_seedsTheMatrixAndMarksOnlyTwoSystemRoles() throws IOException {
        Map<String, String> roles = rolesOf(SCRIPT_001);
        Map<String, List<String>> assignments = assignmentsOf(SCRIPT_001);

        assertEquals(SEEDED_ROLES, roles.keySet());
        assertEquals(SEEDED_ROLES, assignments.keySet());
        for (String role : SEEDED_ROLES) {
            assertEquals(SeedRoles.PERMISSIONS.get(role), assignments.get(role), "Permisos de " + role);
            boolean expectedSystem = role.equals("ESTUDIANTE") || role.equals("ADMIN");
            assertEquals(String.valueOf(expectedSystem).toUpperCase(), roles.get(role),
                    "is_system of " + role);
        }
    }

    /**
     * The change script only moves an existing database towards the creation script, in a single transaction:
     * every permission it inserts is in the catalog of 001 with the same description, every one it deletes is not,
     * every assignment it gives is in the seed of 001 and every one it takes away is not
     *
     * @throws IOException if a script cannot be read
     */
    @Test
    void changeScript_movesAnExistingDatabaseTowardsScript001() throws IOException {
        String sql = Files.readString(Path.of(CHANGE_SCRIPT), StandardCharsets.UTF_8);
        Map<String, String> catalog = permissionsOf(SCRIPT_001);
        Map<String, List<String>> seed = assignmentsOf(SCRIPT_001);

        assertTrue(sql.contains("\nBEGIN;\n"), "Falta BEGIN");
        assertTrue(sql.strip().endsWith("COMMIT;"), "Falta COMMIT");
        Matcher inserted = PERMISSIONS_BLOCK.matcher(sql);
        if (inserted.find()) {
            Matcher row = PERMISSION_ROW.matcher(inserted.group(1));
            while (row.find()) {
                assertEquals(catalog.get(row.group(1)), row.group(2).replace("''", "'"), row.group(1));
            }
        }
        Matcher deleted = DELETED_PERMISSIONS.matcher(sql);
        if (deleted.find()) {
            Matcher code = QUOTED.matcher(deleted.group(1));
            while (code.find()) {
                assertFalse(catalog.containsKey(code.group(1)), code.group(1));
            }
        }
        Matcher given = GIVEN_ASSIGNMENTS.matcher(sql);
        if (given.find()) {
            Matcher pair = PAIR.matcher(given.group(1));
            while (pair.find()) {
                assertTrue(seed.get(pair.group(1)).contains(pair.group(2)), pair.group(1) + " " + pair.group(2));
            }
        }
        Matcher taken = TAKEN_ASSIGNMENTS.matcher(sql);
        if (taken.find()) {
            Matcher pair = PAIR.matcher(taken.group(1));
            while (pair.find()) {
                assertFalse(seed.get(pair.group(1)).contains(pair.group(2)), pair.group(1) + " " + pair.group(2));
            }
        }
    }

    /**
     * Every seeded role has the two permissions that all roles must keep, ESTUDIANTE and ADMIN also have
     * ms01:profile:write and ms01:role:manage belongs only to ADMIN
     */
    @Test
    void seedRoles_satisfyTheMandatoryPermissionRules() {
        for (Map.Entry<String, List<String>> role : SeedRoles.PERMISSIONS.entrySet()) {
            assertTrue(role.getValue().contains("ms01:profile:read"), "profile:read of " + role.getKey());
            assertTrue(role.getValue().contains("ms01:session:logout"), "session:logout of " + role.getKey());
            boolean mustWrite = role.getKey().equals("ESTUDIANTE") || role.getKey().equals("ADMIN");
            if (mustWrite) {
                assertTrue(role.getValue().contains("ms01:profile:write"), "profile:write of " + role.getKey());
            }
            assertEquals(role.getKey().equals("ADMIN"), role.getValue().contains("ms01:role:manage"),
                    "role:manage of " + role.getKey());
        }
    }
}
