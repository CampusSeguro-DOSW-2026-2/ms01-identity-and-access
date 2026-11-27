package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Configuration of the password recovery (RF-MS01-08, RNF-MS01-06): the mail secrets come from the environment, the
 * default values are for local development and the table script exists
 */
class PasswordRecoveryConfigurationTest {

    private static Properties properties;

        /**
     * Loads the application.yaml used by the service
     */
    @BeforeAll
    static void load() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yaml"));
        properties = factory.getObject();
        assertNotNull(properties);
    }

        /**
     * The validity, the attempts and the wait can be overridden with environment variables and by default are 15
     * minutes, 5 attempts and 60 seconds
     */
    @Test
    void recovery_policy_defaultsAreOverridableWithEnvironmentVariables() {
        assertEquals("${RECOVERY_CODE_TTL_MINUTES:15}", properties.getProperty("recovery.code-ttl-minutes"));
        assertEquals("${RECOVERY_MAX_ATTEMPTS:5}", properties.getProperty("recovery.max-attempts"));
        assertEquals("${RECOVERY_RESEND_COOLDOWN_SECONDS:60}",
                properties.getProperty("recovery.resend-cooldown-seconds"));
    }

        /**
     * The email goes out through Gmail with the account of the project: server, port, user and sender have those
     * default values and can be overridden with environment variables; the application password only arrives through
     * the environment and has no default value
     */
    @Test
    void mail_serverAndCredentials_defaultToGmailAndComeFromEnvironmentVariables() {
        assertEquals("${MAIL_HOST:smtp.gmail.com}", properties.getProperty("spring.mail.host"));
        assertEquals("${MAIL_PORT:587}", properties.getProperty("spring.mail.port"));
        assertEquals("${MAIL_USERNAME:proyectometx@gmail.com}", properties.getProperty("spring.mail.username"));
        assertEquals("${MAIL_PASSWORD:}", properties.getProperty("spring.mail.password"));
        assertEquals("${MAIL_FROM:CampusSeguro <proyectometx@gmail.com>}",
                properties.getProperty("recovery.mail-from"));
    }

        /**
     * Gmail requires authentication and STARTTLS: they are active by default, the encryption is mandatory (it is not
     * sent in clear text if the server does not offer it) and both can be turned off through the environment for a test
     * server such as Mailpit
     */
    @Test
    void mail_authenticationAndStartTls_areOnByDefaultAndConfigurable() {
        assertEquals("${MAIL_SMTP_AUTH:true}", properties.getProperty("spring.mail.properties.mail.smtp.auth"));
        assertEquals("${MAIL_SMTP_STARTTLS:true}",
                properties.getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
        assertEquals("${MAIL_SMTP_STARTTLS:true}",
                properties.getProperty("spring.mail.properties.mail.smtp.starttls.required"));
    }

        /**
     * The health of MS01 does not depend on the external mail server: if Gmail does not respond only the password
     * recovery is affected
     */
    @Test
    void mail_healthIndicator_isOffByDefault() {
        assertEquals("${MANAGEMENT_HEALTH_MAIL_ENABLED:false}", properties.getProperty("management.health.mail.enabled"));
    }

        /**
     * The mail server has maximum times so that a down server does not leave blocked threads
     */
    @Test
    void mail_smtpTimeouts_areBounded() {
        assertEquals("5000", properties.getProperty("spring.mail.properties.mail.smtp.connectiontimeout"));
        assertEquals("5000", properties.getProperty("spring.mail.properties.mail.smtp.timeout"));
        assertEquals("5000", properties.getProperty("spring.mail.properties.mail.smtp.writetimeout"));
    }

    /**
     * The creation script and the index script define the recovery codes table of the data model: columns, foreign
     * key, check and index
     *
     * @throws IOException if a script cannot be read
     */
    @Test
    void databaseScripts_createTheRecoveryCodesTableOfTheDataModel() throws IOException {
        String schema = Files.readString(Path.of("database", "001_create_schema.sql"), StandardCharsets.UTF_8);
        String indexes = Files.readString(Path.of("database", "002_index_schema.sql"), StandardCharsets.UTF_8);

        assertTrue(schema.contains("CREATE TABLE password_reset_codes"));
        assertTrue(schema.contains("user_id UUID NOT NULL"));
        assertTrue(schema.contains("code_hash VARCHAR(100) NOT NULL"));
        assertTrue(schema.contains("expires_at TIMESTAMPTZ NOT NULL"));
        assertTrue(schema.contains("used_at TIMESTAMPTZ DEFAULT NULL"));
        assertTrue(schema.contains("created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(schema.contains("REFERENCES users(id)"));
        assertTrue(schema.indexOf("CREATE TABLE password_reset_codes") > schema.indexOf("CREATE TABLE users"));
        assertTrue(indexes.contains("idx_password_reset_codes_user ON password_reset_codes (user_id, expires_at)"));
    }

    /**
     * There is no separate script for the recovery codes table: it is part of the creation script
     *
     * @throws IOException if the folder cannot be read
     */
    @Test
    void databaseFolder_hasNoSeparateScriptForTheRecoveryCodesTable() throws IOException {
        try (var scripts = Files.list(Path.of("database"))) {
            var names = scripts.map(path -> path.getFileName().toString()).sorted().toList();

            assertEquals("001_create_schema.sql", names.get(0));
            assertEquals("002_index_schema.sql", names.get(1));
            assertFalse(names.stream().anyMatch(name -> name.startsWith("003")));
        }
    }
}
