package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * X-User-Role no contiene ningun rol con el permiso que exige la operacion (x-permiso del contrato).
 * Se responde 403 PROHIBIDO.
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException() {
        super("No cuenta con los permisos suficientes para realizar esta operación.");
    }
}
