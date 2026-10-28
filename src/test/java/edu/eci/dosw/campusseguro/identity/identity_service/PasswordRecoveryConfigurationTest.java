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
 * Configuración de la recuperación de contraseña (RF-MS01-08, RNF-MS01-06): los secretos del correo salen del
 * entorno, los valores por defecto sirven para desarrollo local y el script de la tabla existe
 */
class PasswordRecoveryConfigurationTest {

    private static Properties properties;

    /**
     * Carga el application.yaml que usa el servicio
     */
    @BeforeAll
    static void load() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yaml"));
        properties = factory.getObject();
        assertNotNull(properties);
    }

    /**
     * La vigencia, los intentos y la espera se pueden sobrescribir con variables de entorno y por defecto son
     * 15 minutos, 5 intentos y 60 segundos
     */
    @Test
    void recovery_policy_defaultsAreOverridableWithEnvironmentVariables() {
        assertEquals("${RECOVERY_CODE_TTL_MINUTES:15}", properties.getProperty("recovery.code-ttl-minutes"));
        assertEquals("${RECOVERY_MAX_ATTEMPTS:5}", properties.getProperty("recovery.max-attempts"));
        assertEquals("${RECOVERY_RESEND_COOLDOWN_SECONDS:60}",
                properties.getProperty("recovery.resend-cooldown-seconds"));
    }

    /**
     * El servidor de correo y sus credenciales salen de variables de entorno y nunca están escritos en el
     * archivo, la contraseña no tiene valor por defecto
     */
    @Test
    void mail_serverAndCredentials_comeFromEnvironmentVariables() {
        assertEquals("${MAIL_HOST:localhost}", properties.getProperty("spring.mail.host"));
        assertEquals("${MAIL_PORT:1025}", properties.getProperty("spring.mail.port"));
        assertEquals("${MAIL_USERNAME:}", properties.getProperty("spring.mail.username"));
        assertEquals("${MAIL_PASSWORD:}", properties.getProperty("spring.mail.password"));
        assertEquals("${MAIL_FROM:no-reply@campusseguro.local}", properties.getProperty("recovery.mail-from"));
    }

    /**
     * La autenticación y el cifrado SMTP se activan con variables de entorno y están apagados por defecto, que
     * es lo que necesita el servidor de correo de desarrollo
     */
    @Test
    void mail_authenticationAndStartTls_areOffByDefaultAndConfigurable() {
        assertEquals("${MAIL_SMTP_AUTH:false}", properties.getProperty("spring.mail.properties.mail.smtp.auth"));
        assertEquals("${MAIL_SMTP_STARTTLS:false}",
                properties.getProperty("spring.mail.properties.mail.smtp.starttls.enable"));
    }

    /**
     * El servidor de correo tiene tiempos máximos para que un servidor caído no deje hilos bloqueados
     */
    @Test
    void mail_smtpTimeouts_areBounded() {
        assertEquals("5000", properties.getProperty("spring.mail.properties.mail.smtp.connectiontimeout"));
        assertEquals("5000", properties.getProperty("spring.mail.properties.mail.smtp.timeout"));
        assertEquals("5000", properties.getProperty("spring.mail.properties.mail.smtp.writetimeout"));
    }

    /**
     * El script de la tabla de códigos sigue el modelo de datos: columnas, clave foránea, índice y comprobación
     *
     * @throws IOException si no se puede leer el script
     */
    @Test
    void databaseScript_createsTheRecoveryCodesTableOfTheDataModel() throws IOException {
        String script = Files.readString(Path.of("database", "003_codigos_recuperacion.sql"),
                StandardCharsets.UTF_8);

        assertTrue(script.contains("codigos_recuperacion"));
        assertTrue(script.contains("usuario_id UUID NOT NULL"));
        assertTrue(script.contains("codigo_hash VARCHAR(100) NOT NULL"));
        assertTrue(script.contains("expira_en TIMESTAMPTZ NOT NULL"));
        assertTrue(script.contains("usado_en TIMESTAMPTZ DEFAULT NULL"));
        assertTrue(script.contains("creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(script.contains("REFERENCES usuarios(id)"));
        assertTrue(script.contains("idx_codigos_usuario ON codigos_recuperacion (usuario_id, expira_en)"));
        assertFalse(script.toLowerCase().contains("codigo VARCHAR"));
    }

    /**
     * Los scripts de la base de datos se ejecutan en orden numérico y el de los códigos va después del esquema
     * de usuarios y de sus índices
     *
     * @throws IOException si no se puede leer la carpeta de scripts
     */
    @Test
    void databaseScripts_areNumberedSoTheRecoveryTableIsCreatedAfterTheUsers() throws IOException {
        try (var scripts = Files.list(Path.of("database"))) {
            var names = scripts.map(path -> path.getFileName().toString()).sorted().toList();

            assertEquals("001_create_schema.sql", names.get(0));
            assertEquals("002_index_schema.sql", names.get(1));
            assertEquals("003_codigos_recuperacion.sql", names.get(2));
        }
    }
}
