package edu.eci.dosw.campusseguro.identity.identity_service.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;

/**
 * Delivers to the controllers the identity of the requester (RequesterIdentity) built with the headers X-User-Id,
 * X-User-Role and X-User-Permission injected by the API Gateway (RN-MS01-23, RG-07), the identity is never taken from
 * the body of the request
 */
public class RequesterIdentityArgumentResolver implements HandlerMethodArgumentResolver {

        /**
     * Indicates that this resolver handles the parameters of type RequesterIdentity
     *
     * @param parameter parameter of the controller method
     * @return true if the parameter is of type RequesterIdentity
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return RequesterIdentity.class.equals(parameter.getParameterType());
    }

        /**
     * Builds the identity of the requester from the headers of the request
     *
     * @param parameter parameter of the controller method
     * @param mavContainer container of the model and the view, it is not used
     * @param webRequest current request
     * @param binderFactory factory of data binders, it is not used
     * @return the identity of the requester
     * @throws UnauthenticatedException if the identity headers are missing or their value is not valid
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return IdentityHeaderParser.parse(
                webRequest.getHeader(IdentityHeaderParser.USER_ID_HEADER),
                webRequest.getHeader(IdentityHeaderParser.USER_ROLE_HEADER),
                webRequest.getHeader(IdentityHeaderParser.USER_PERMISSION_HEADER));
    }
}
