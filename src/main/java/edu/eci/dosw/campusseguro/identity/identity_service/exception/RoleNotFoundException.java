package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The requested role does not exist, it is translated to HTTP 404 with code NO_ENCONTRADO and the message "Rol no
 * encontrado." of the forms F-11, F-12 and F-13
 */
public class RoleNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Rol no encontrado.";

    private final String roleName;

        /**
     * Creates the exception with the name of the role that does not exist, the name is not included in the message that
     * the client receives
     *
     * @param roleName name of the role that was not found
     */
    public RoleNotFoundException(String roleName) {
        super(MESSAGE);
        this.roleName = roleName;
    }

        /**
     * Returns the name of the role that does not exist
     *
     * @return the name not found
     */
    public String getRoleName() {
        return roleName;
    }
}
