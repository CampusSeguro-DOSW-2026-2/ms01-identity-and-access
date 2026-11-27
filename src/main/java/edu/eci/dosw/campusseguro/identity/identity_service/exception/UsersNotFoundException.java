package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.List;
import java.util.UUID;

/**
 * One or more users of the request do not exist. It is translated to HTTP 404 with code NO_ENCONTRADO and the message
 * "Usuario no encontrado." of the forms F-08 and F-09. The batch operations are atomic: when this exception is thrown
 * no change is applied
 */
public class UsersNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Usuario no encontrado.";

    private final List<UUID> missingIds;

        /**
     * Creates the exception with the identifiers that do not exist
     *
     * @param missingIds identifiers of the users that were not found
     */
    public UsersNotFoundException(List<UUID> missingIds) {
        super(MESSAGE);
        this.missingIds = List.copyOf(missingIds);
    }

        /**
     * Returns the identifiers of the users that do not exist
     *
     * @return immutable list with the identifiers not found
     */
    public List<UUID> getMissingIds() {
        return missingIds;
    }
}
