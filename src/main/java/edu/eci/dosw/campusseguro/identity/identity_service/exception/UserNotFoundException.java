package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.UUID;

/**
 * El usuario pedido no existe, se traduce a HTTP 404 con código NO_ENCONTRADO y el mensaje
 * "Usuario no encontrado." de los formularios F-08 y F-09
 */
public class UserNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Usuario no encontrado.";

    private final UUID userId;

    /**
     * Crea la excepción con el identificador que no existe, el identificador no se incluye en el mensaje
     * que recibe el cliente
     *
     * @param id identificador del usuario que no se encontró
     */
    public UserNotFoundException(UUID id) {
        super(MESSAGE);
        this.userId = id;
    }

    /**
     * Devuelve el identificador del usuario que no existe
     *
     * @return el identificador no encontrado
     */
    public UUID getUserId() {
        return userId;
    }
}
