package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of POST /api/v1/auth/register (RF-MS01-01): the confirmation message and the registration token, which
 * only whoever made this registration receives and which is required, together with the code sent to the email, to
 * confirm it and to ask for a new code (F-14)
 *
 * @param mensaje confirmation text for the user
 * @param token registration token, it is only valid for the last registration of the email
 */
public record RegistrationAcceptedResponse(String mensaje, @JsonProperty("token_registro") String token) {
}
