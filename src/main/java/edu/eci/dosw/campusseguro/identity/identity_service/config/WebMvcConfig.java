package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;

/**
 * Configuración de Spring MVC, MS01 no configura CORS porque se gestiona únicamente en el API Gateway
 * (RNF-MS01-03, Estándares de Integración, sección 2.1)
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Registra el resolvedor que entrega la identidad del solicitante a los controladores
     *
     * @param resolvers lista de resolvedores de argumentos a la que se agrega el de la identidad
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterIdentityArgumentResolver());
    }
}
