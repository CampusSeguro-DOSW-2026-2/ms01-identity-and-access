package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Response with a confirmation message for the operations that return no data (MensajeResponse of the contract), for
 * example the logout (RF-MS01-04)
 *
 * @param mensaje confirmation text for the user
 */
public record MessageResponse(String mensaje) {
}
