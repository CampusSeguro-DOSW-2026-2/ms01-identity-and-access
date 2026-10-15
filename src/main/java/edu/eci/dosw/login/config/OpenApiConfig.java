package edu.eci.dosw.login.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ms01OpenAPI() {
        return new OpenAPI().info(new Info()
                .title("MS01 - Identity and Access API")
                .description("Dominio funcional de Identidad y Acceso (Fase 1 - MVP sin JWT)")
                .version("v1"));
    }
}