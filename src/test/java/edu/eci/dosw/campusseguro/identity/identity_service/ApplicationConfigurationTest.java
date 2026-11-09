package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Configuración base del servicio exigida por los estándares generales, puerto del módulo, credenciales de
 * la base de datos con valores por defecto de desarrollo, esquema controlado por scripts, métricas mínimas y
 * documentación de la API apagada por defecto
 */
class ApplicationConfigurationTest {

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
     * El servicio escucha en el puerto asignado al módulo
     */
    @Test
    void server_listensOnThePortOfTheModule() {
        assertEquals("8081", properties.getProperty("server.port"));
    }

    /**
     * Las credenciales de la base de datos se pueden sobrescribir con variables de entorno y, si no se definen,
     * se usan las de desarrollo
     */
    @Test
    void datasourceCredentials_defaultToTheDevelopmentOnes() {
        assertEquals("${DB_USERNAME:campus_user}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD:campus_password}", properties.getProperty("spring.datasource.password"));
    }

    /**
     * El esquema lo crean los scripts, Hibernate solo lo valida
     */
    @Test
    void jpa_onlyValidatesTheSchema() {
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", properties.getProperty("spring.jpa.open-in-view"));
    }

    /**
     * Las fechas se manejan en UTC
     */
    @Test
    void jpa_usesUtc() {
        assertEquals("UTC", properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"));
    }

    /**
     * Solo se expone el estado de salud, sin detalles
     */
    @Test
    void actuator_exposesOnlyTheHealthWithoutDetails() {
        assertEquals("health", properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-details"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-components"));
    }

    /**
     * La documentación de la API solo se activa de forma explícita
     */
    @Test
    void swagger_isDisabledByDefault() {
        assertEquals("${SWAGGER_ENABLED:false}", properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals("${SWAGGER_ENABLED:false}", properties.getProperty("springdoc.swagger-ui.enabled"));
    }

    /**
     * Cada línea de log lleva el correlation id
     */
    @Test
    void logPattern_includesTheCorrelationId() {
        String pattern = properties.getProperty("logging.pattern.console");

        assertNotNull(pattern);
        assertTrue(pattern.contains("%X{correlation_id"));
    }

    /**
     * La página de error por defecto está apagada para responder siempre con el cuerpo estándar
     */
    @Test
    void whitelabelErrorPage_isDisabled() {
        assertEquals("false", properties.getProperty("server.error.whitelabel.enabled"));
    }

    /**
     * Los dominios institucionales salen de variables de entorno
     */
    @Test
    void institutionalDomains_areConfiguredFromTheEnvironment() {
        assertTrue(properties.getProperty("identity.institutional-domains").contains("INSTITUTIONAL_EMAIL_DOMAINS"));
    }

    /**
     * Los valores globales de los estándares no se configuran: el emisor, la audiencia y la vigencia del JWT
     * y el timeout de las llamadas internas viven en el código
     */
    @Test
    void globalValues_areNotConfigurable() {
        assertNull(properties.getProperty("jwt.expiration-minutes"));
        assertNull(properties.getProperty("jwt.issuer"));
        assertNull(properties.getProperty("jwt.audience"));
        assertNull(properties.getProperty("gateway.timeout-ms"));
    }

    /**
     * Si la variable de dominios no está definida se permiten los dos dominios institucionales
     */
    @Test
    void institutionalDomains_defaultToBothInstitutionalDomains() {
        assertEquals("${INSTITUTIONAL_EMAIL_DOMAINS:escuelaing.edu.co,mail.escuelaing.edu.co}",
                properties.getProperty("identity.institutional-domains"));
    }

    /**
     * La llave privada del JWT no tiene un valor por defecto embebido
     */
    @Test
    void jwtPrivateKey_hasNoEmbeddedDefault() {
        assertEquals("${JWT_PRIVATE_KEY:}", properties.getProperty("jwt.private-key"));
        assertFalse(properties.getProperty("jwt.private-key").contains("BEGIN"));
    }
}
