package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * RG-08 / RN-MS01-28: los scripts que crean la tabla roles protegen en la base de datos el nombre reservado
 * DETAILS, el mismo que rechaza el validador de la API
 */
class RoleSchemaScriptsTest {

    private static final Pattern RESERVED_CHECK = Pattern.compile(
            "CONSTRAINT\\s+chh_role_name_reserved\\s+CHECK\\s*\\(\\s*nombre\\s*<>\\s*'([A-Z_]+)'\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lee un script de base de datos
     *
     * @param script ruta del script, relativa a la raíz del proyecto
     * @return el contenido del script
     * @throws IOException si el script no se puede leer
     */
    private static String read(String script) throws IOException {
        return Files.readString(Path.of(script), StandardCharsets.UTF_8);
    }

    /**
     * Tanto el script de creación como la migración declaran la restricción CHECK con el nombre reservado
     *
     * @param script ruta del script
     * @throws IOException si el script no se puede leer
     */
    @ParameterizedTest
    @ValueSource(strings = {"database/001_create_schema.sql", "database/migraciones/004_migracion_roles_permisos.sql"})
    void rolesTable_rejectsTheReservedNameInTheDatabase(String script) throws IOException {
        Matcher check = RESERVED_CHECK.matcher(read(script));

        assertTrue(check.find(), "Falta chh_role_name_reserved en " + script);
        assertEquals(RoleNames.RESERVED, check.group(1));
    }

    /**
     * La restricción del nombre reservado se declara una sola vez en cada script
     *
     * @param script ruta del script
     * @throws IOException si el script no se puede leer
     */
    @ParameterizedTest
    @ValueSource(strings = {"database/001_create_schema.sql", "database/migraciones/004_migracion_roles_permisos.sql"})
    void rolesTable_declaresTheReservedNameCheckOnlyOnce(String script) throws IOException {
        Matcher check = RESERVED_CHECK.matcher(read(script));
        int count = 0;
        while (check.find()) {
            count++;
        }

        assertEquals(1, count);
    }
}
