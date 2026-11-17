package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Base of the bodies of the role management requests (RF-MS01-10), any field that the contract does not define is
 * rejected with the message "Campo no permitido.", especially es_sistema, which is only returned and never received
 */
public abstract class RoleRequest extends StrictRequest {

    public static final String ROLE_UNKNOWN_FIELD_MESSAGE = "Campo no permitido.";

        /**
     * Error message of a property not defined in the role forms
     *
     * @param fieldName name of the undefined property
     * @return the message of the forms F-10, F-11 and F-12
     */
    @Override
    public String messageForUnknownField(String fieldName) {
        return ROLE_UNKNOWN_FIELD_MESSAGE;
    }
}
