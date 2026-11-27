package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;

/**
 * Spring MVC configuration, MS01 does not configure CORS because it is managed only in the API Gateway (RNF-MS01-03,
 * Integration Standards, section 2.1)
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        /**
     * Registers the resolver that delivers the identity of the requester to the controllers
     *
     * @param resolvers list of argument resolvers to which the identity one is added
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterIdentityArgumentResolver());
    }
}
