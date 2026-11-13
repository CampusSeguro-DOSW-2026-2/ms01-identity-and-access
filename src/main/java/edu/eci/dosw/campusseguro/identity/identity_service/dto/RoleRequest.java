package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Base de los cuerpos de las peticiones de gestión de roles (RF-MS01-10), cualquier campo que el contrato no
 * defina se rechaza con el mensaje "Campo no permitido.", en especial es_sistema, que solo se devuelve y
 * nunca se recibe
 */
public abstract class RoleRequest extends StrictRequest {

    public static final String ROLE_UNKNOWN_FIELD_MESSAGE = "Campo no permitido.";

    /**
     * Mensaje de error de una propiedad no definida en los formularios de roles
     *
     * @param fieldName nombre de la propiedad no definida
     * @return el mensaje de los formularios F-10, F-11 y F-12
     */
    @Override
    public String messageForUnknownField(String fieldName) {
        return ROLE_UNKNOWN_FIELD_MESSAGE;
    }
}
