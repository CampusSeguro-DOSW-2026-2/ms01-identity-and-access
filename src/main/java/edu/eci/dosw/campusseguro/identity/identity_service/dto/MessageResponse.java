package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Respuesta con un mensaje de confirmación para las operaciones que no devuelven datos (MensajeResponse del
 * contrato), por ejemplo el cierre de sesión (RF-MS01-04)
 *
 * @param mensaje texto de confirmación para el usuario
 */
public record MessageResponse(String mensaje) {
}
