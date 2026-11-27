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
 * Base configuration of the service required by the general standards, port of the module, database credentials with
 * development default values, schema controlled by scripts, minimal metrics and API documentation turned off by default
 */
class ApplicationConfigurationTest {

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
     * The service listens on the port assigned to the module
     */
    @Test
    void server_listensOnThePortOfTheModule() {
        assertEquals("8081", properties.getProperty("server.port"));
    }

        /**
     * The database credentials can be overridden with environment variables and, if they are not defined, the
     * development ones are used
     */
    @Test
    void datasourceCredentials_defaultToTheDevelopmentOnes() {
        assertEquals("${DB_USERNAME:campus_user}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD:campus_password}", properties.getProperty("spring.datasource.password"));
    }

        /**
     * The schema is created by the scripts, Hibernate only validates it
     */
    @Test
    void jpa_onlyValidatesTheSchema() {
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", properties.getProperty("spring.jpa.open-in-view"));
    }

        /**
     * Dates are handled in UTC
     */
    @Test
    void jpa_usesUtc() {
        assertEquals("UTC", properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"));
    }

        /**
     * Only the health status is exposed, without details
     */
    @Test
    void actuator_exposesOnlyTheHealthWithoutDetails() {
        assertEquals("health", properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-details"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-components"));
    }

        /**
     * The API documentation is only activated explicitly
     */
    @Test
    void swagger_isDisabledByDefault() {
        assertEquals("${SWAGGER_ENABLED:false}", properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals("${SWAGGER_ENABLED:false}", properties.getProperty("springdoc.swagger-ui.enabled"));
    }

        /**
     * Each log line carries the correlation id
     */
    @Test
    void logPattern_includesTheCorrelationId() {
        String pattern = properties.getProperty("logging.pattern.console");

        assertNotNull(pattern);
        assertTrue(pattern.contains("%X{correlation_id"));
    }

        /**
     * The default error page is turned off to always answer with the standard body
     */
    @Test
    void whitelabelErrorPage_isDisabled() {
        assertEquals("false", properties.getProperty("server.error.whitelabel.enabled"));
    }

        /**
     * The institutional domains come from environment variables
     */
    @Test
    void institutionalDomains_areConfiguredFromTheEnvironment() {
        assertTrue(properties.getProperty("identity.institutional-domains").contains("INSTITUTIONAL_EMAIL_DOMAINS"));
    }

        /**
     * The global values of the standards are not configured: the issuer, the audience and the validity of the JWT and
     * the timeout of the internal calls live in the code
     */
    @Test
    void globalValues_areNotConfigurable() {
        assertNull(properties.getProperty("jwt.expiration-minutes"));
        assertNull(properties.getProperty("jwt.issuer"));
        assertNull(properties.getProperty("jwt.audience"));
        assertNull(properties.getProperty("gateway.timeout-ms"));
    }

        /**
     * If the domains variable is not defined both institutional domains are allowed
     */
    @Test
    void institutionalDomains_defaultToBothInstitutionalDomains() {
        assertEquals("${INSTITUTIONAL_EMAIL_DOMAINS:escuelaing.edu.co,mail.escuelaing.edu.co}",
                properties.getProperty("identity.institutional-domains"));
    }

        /**
     * The JWT private key has no embedded default value
     */
    @Test
    void jwtPrivateKey_hasNoEmbeddedDefault() {
        assertEquals("${JWT_PRIVATE_KEY:}", properties.getProperty("jwt.private-key"));
        assertFalse(properties.getProperty("jwt.private-key").contains("BEGIN"));
    }
}
