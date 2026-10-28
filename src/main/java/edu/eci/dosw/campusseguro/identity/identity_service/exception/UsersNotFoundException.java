package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.List;
import java.util.UUID;

/**
 * Uno o mas usuarios de la peticion no existen. Se traduce a HTTP 404 con codigo NO_ENCONTRADO y el
 * mensaje "Usuario no encontrado." de los formularios F-08 y F-09. Las operaciones por lote son
 * atomicas: cuando se lanza esta excepcion no se aplica ningun cambio
 */
public class UsersNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Usuario no encontrado.";

    private final List<UUID> missingIds;

    /**
     * Crea la excepcion con los identificadores que no existen
     *
     * @param missingIds identificadores de los usuarios que no se encontraron
     */
    public UsersNotFoundException(List<UUID> missingIds) {
        super(MESSAGE);
        this.missingIds = List.copyOf(missingIds);
    }

    /**
     * Devuelve los identificadores de los usuarios que no existen
     *
     * @return lista inmutable con los identificadores no encontrados
     */
    public List<UUID> getMissingIds() {
        return missingIds;
    }
}
