package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PendingRegistrationEntity;
import jakarta.persistence.Column;

/**
 * Coherence of the table pending_registrations (RF-MS01-01) between the schema of a new database (001 and 002) and
 * the entity that Hibernate validates when MS01 starts
 */
class PendingRegistrationsSchemaScriptsTest {

    private static final String SCRIPT_001 = "database/001_create_schema.sql";
    private static final String SCRIPT_002 = "database/002_index_schema.sql";
    private static final String TABLE_START = "CREATE TABLE pending_registrations (";

        /**
     * Reads a script of the repository
     *
     * @param script path of the script from the root of the repository
     * @return the content of the script
     * @throws IOException if the script cannot be read
     */
    private static String read(String script) throws IOException {
        return Files.readString(Path.of(script), StandardCharsets.UTF_8);
    }

        /**
     * Extracts the definition of the table pending_registrations from a script
     *
     * @param script path of the script
     * @return the CREATE TABLE statement until its closing parenthesis
     * @throws IOException if the script cannot be read
     */
    private static String table(String script) throws IOException {
        String sql = read(script);
        int start = sql.indexOf(TABLE_START);
        assertTrue(start >= 0, "pending_registrations is missing in " + script);
        return sql.substring(start, sql.indexOf(");", start) + 2);
    }

        /**
     * The email is the primary key, so a new registration attempt overwrites the previous one
     *
     * @throws IOException if a script cannot be read
     */
    @Test
    void table_emailIsThePrimaryKey() throws IOException {
        assertTrue(table(SCRIPT_001).contains("email VARCHAR(100) PRIMARY KEY"));
    }

        /**
     * Every column of the entity exists in the table, so the validation of the schema does not fail at startup
     *
     * @throws IOException if a script cannot be read
     */
    @Test
    void table_hasEveryColumnOfTheEntity() throws IOException {
        String table = table(SCRIPT_001);
        for (var field : PendingRegistrationEntity.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                assertTrue(table.contains("    " + column.name() + " "), column.name() + " is missing");
            }
        }
    }

        /**
     * The index of the scheduled deletion exists on a new database
     *
     * @throws IOException if a script cannot be read
     */
    @Test
    void expirationIndex_existsInTheSchema() throws IOException {
        assertTrue(read(SCRIPT_002).contains("idx_pending_registrations_expires ON pending_registrations (expires_at)"));
    }
}
