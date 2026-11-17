package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * RG-08 / RN-MS01-28: the scripts that create the roles table protect the reserved name DETAILS in the database,
 * the same one that the API validator rejects, and the function that checks the permissions of each role keeps the
 * mandatory permissions
 */
class RoleSchemaScriptsTest {

    private static final String SCRIPT_001 = "database/001_create_schema.sql";
    private static final String SCRIPT_004 = "database/migrations/004_migration_roles_permissions.sql";

    private static final Pattern RESERVED_CHECK = Pattern.compile(
            "CONSTRAINT\\s+chh_role_name_reserved\\s+CHECK\\s*\\(\\s*name\\s*<>\\s*'([A-Z_]+)'\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Reads a database script
     *
     * @param script path of the script, relative to the project root
     * @return the content of the script
     * @throws IOException if the script cannot be read
     */
    private static String read(String script) throws IOException {
        return Files.readString(Path.of(script), StandardCharsets.UTF_8);
    }

    /**
     * Both the creation script and the migration declare the CHECK constraint with the reserved name
     *
     * @param script path of the script
     * @throws IOException if the script cannot be read
     */
    @ParameterizedTest
    @ValueSource(strings = {SCRIPT_001, SCRIPT_004})
    void rolesTable_rejectsTheReservedNameInTheDatabase(String script) throws IOException {
        Matcher check = RESERVED_CHECK.matcher(read(script));

        assertTrue(check.find(), "chh_role_name_reserved is missing in " + script);
        assertEquals(RoleNames.RESERVED, check.group(1));
    }

    /**
     * The reserved name constraint is declared only once in each script
     *
     * @param script path of the script
     * @throws IOException if the script cannot be read
     */
    @ParameterizedTest
    @ValueSource(strings = {SCRIPT_001, SCRIPT_004})
    void rolesTable_declaresTheReservedNameCheckOnlyOnce(String script) throws IOException {
        Matcher check = RESERVED_CHECK.matcher(read(script));
        int count = 0;
        while (check.find()) {
            count++;
        }

        assertEquals(1, count);
    }

    /**
     * The function that checks the permissions of each role requires ms01:profile:read and ms01:session:logout in
     * every role, ms01:profile:write in ESTUDIANTE and ADMIN and ms01:role:manage only in ADMIN
     *
     * @param script path of the script
     * @throws IOException if the script cannot be read
     */
    @ParameterizedTest
    @ValueSource(strings = {SCRIPT_001, SCRIPT_004})
    void checkRolePermissions_enforcesTheMandatoryPermissions(String script) throws IOException {
        String sql = read(script);

        assertTrue(sql.contains("p.name IN ('ms01:profile:read', 'ms01:session:logout')) < 2"));
        assertTrue(sql.contains("v_name IN ('ESTUDIANTE', 'ADMIN')"));
        assertTrue(sql.contains("p.name = 'ms01:profile:write'"));
        assertTrue(sql.contains("Role ADMIN must keep the permission ms01:role:manage"));
        assertTrue(sql.contains("belongs only to the role ADMIN"));
    }

    /**
     * The old rule that required at least one permission is gone, the two mandatory permissions replace it
     *
     * @param script path of the script
     * @throws IOException if the script cannot be read
     */
    @ParameterizedTest
    @ValueSource(strings = {SCRIPT_001, SCRIPT_004})
    void checkRolePermissions_doesNotRequireAtLeastOnePermissionAnymore(String script) throws IOException {
        assertFalse(read(script).contains("at least one permission"));
    }

    /**
     * The database folder keeps the creation and index scripts at its root, which Postgres runs when it creates a
     * database, and the migration and the maintenance script in subfolders, which it does not run
     *
     * @throws IOException if the folder cannot be read
     */
    @Test
    void databaseFolder_keepsOnlyTheCreationScriptsAtTheRoot() throws IOException {
        try (var scripts = Files.list(Path.of("database"))) {
            var names = scripts.map(path -> path.getFileName().toString()).sorted().toList();

            assertEquals(java.util.List.of("001_create_schema.sql", "002_index_schema.sql", "maintenance",
                    "migrations"), names);
        }
        assertTrue(Files.exists(Path.of(SCRIPT_004)));
        assertTrue(Files.exists(Path.of("database/maintenance/005_maintenance_roles_permissions.sql")));
    }
}
