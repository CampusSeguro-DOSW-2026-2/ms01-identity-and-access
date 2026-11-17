package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.UUID;

/**
 * The requested user does not exist, it is translated to HTTP 404 with code NO_ENCONTRADO and the message "Usuario no
 * encontrado." of the forms F-08 and F-09
 */
public class UserNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Usuario no encontrado.";

    private final UUID userId;

        /**
     * Creates the exception with the identifier that does not exist, the identifier is not included in the message that
     * the client receives
     *
     * @param id identifier of the user that was not found
     */
    public UserNotFoundException(UUID id) {
        super(MESSAGE);
        this.userId = id;
    }

        /**
     * Returns the identifier of the user that does not exist
     *
     * @return the identifier not found
     */
    public UUID getUserId() {
        return userId;
    }
}
