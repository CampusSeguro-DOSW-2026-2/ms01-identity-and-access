package edu.eci.dosw.campusseguro.identity.identity_service.validation;

/**
 * Reglas y mensajes de validación compartidos por los formularios de MS01 (2_forms-inputs.md), están
 * en un solo lugar para que el registro, el perfil y los futuros flujos de contraseña usen exactamente la
 * misma definición
 */
public final class FieldRules {

    public static final String NAME_PATTERN = "^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' -]+$";

    public static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s])\\S{8,64}$";

    public static final int NAME_MIN_LENGTH = 2;
    public static final int NAME_MAX_LENGTH = 30;
    public static final int EMAIL_MAX_LENGTH = 100;
    public static final int PASSWORD_MAX_LENGTH = 64;

    public static final String FIRST_NAME_MESSAGE = "El primer nombre debe tener entre 2 y 30 letras.";
    public static final String SECOND_NAME_MESSAGE = "El segundo nombre debe tener entre 2 y 30 letras.";
    public static final String FIRST_LAST_NAME_MESSAGE = "El primer apellido debe tener entre 2 y 30 letras.";
    public static final String SECOND_LAST_NAME_MESSAGE = "El segundo apellido debe tener entre 2 y 30 letras.";
    public static final String EMAIL_REGISTER_MESSAGE = "Correo no válido.";
    public static final String EMAIL_DUPLICATED_MESSAGE = "El correo ya está registrado.";
    public static final String PASSWORD_MESSAGE =
            "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial.";
    public static final String PASSWORDS_MISMATCH_MESSAGE = "Las contraseñas no coinciden.";

    /**
     * No se instancia
     */
    private FieldRules() {
    }
}
