package edu.eci.dosw.campusseguro.identity.identity_service.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;

/**
 * Entrega a los controladores la identidad del solicitante (RequesterIdentity) construida con las
 * cabeceras X-User-Id y X-User-Role que inyecta el API Gateway (RN-MS01-23, RG-07), la identidad nunca se
 * toma del cuerpo de la petición
 */
public class RequesterIdentityArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Indica que este resolvedor atiende los parámetros de tipo RequesterIdentity
     *
     * @param parameter parámetro del método del controlador
     * @return true si el parámetro es de tipo RequesterIdentity
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return RequesterIdentity.class.equals(parameter.getParameterType());
    }

    /**
     * Construye la identidad del solicitante a partir de las cabeceras de la petición
     *
     * @param parameter parámetro del método del controlador
     * @param mavContainer contenedor del modelo y la vista, no se usa
     * @param webRequest petición en curso
     * @param binderFactory fábrica de enlazadores de datos, no se usa
     * @return la identidad del solicitante
     * @throws UnauthenticatedException si faltan las cabeceras de identidad o su valor no es válido
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return IdentityHeaderParser.parse(
                webRequest.getHeader(IdentityHeaderParser.USER_ID_HEADER),
                webRequest.getHeader(IdentityHeaderParser.USER_ROLE_HEADER));
    }
}
