package edu.eci.dosw.login.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion web para permitir el consumo desde el microservicio de frontend
 * durante el desarrollo.
 *
 * Nota: en la FASE 2 esta configuracion se coordinara con el API Gateway.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserRolRequestConverter userRolRequestConverter;

    public WebConfig(UserRolRequestConverter userRolRequestConverter) {
        this.userRolRequestConverter = userRolRequestConverter;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * Registra la conversion de los roles recibidos por query param o path,
     * de modo que {@code ?rol=COORDINADOR_DE_EMERGENCIAS} y
     * {@code ?rol=COORDINADOR DE EMERGENCIAS} sean ambos validos.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(userRolRequestConverter);
    }
}