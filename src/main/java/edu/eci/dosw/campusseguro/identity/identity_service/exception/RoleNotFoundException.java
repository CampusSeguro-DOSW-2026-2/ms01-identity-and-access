package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * El rol pedido no existe, se traduce a HTTP 404 con código NO_ENCONTRADO y el mensaje "Rol no encontrado."
 * de los formularios F-11, F-12 y F-13
 */
public class RoleNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Rol no encontrado.";

    private final String roleName;

    /**
     * Crea la excepción con el nombre del rol que no existe, el nombre no se incluye en el mensaje que
     * recibe el cliente
     *
     * @param roleName nombre del rol que no se encontró
     */
    public RoleNotFoundException(String roleName) {
        super(MESSAGE);
        this.roleName = roleName;
    }

    /**
     * Devuelve el nombre del rol que no existe
     *
     * @return el nombre no encontrado
     */
    public String getRoleName() {
        return roleName;
    }
}
