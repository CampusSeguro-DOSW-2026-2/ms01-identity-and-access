package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;

/**
 * Already validated criteria of the internal query of users by role (RF-MS01-09)
 *
 * @param role role that the users must have
 * @param createdBefore cut-off instant, inclusive, null if it is not limited by creation date
 * @param page page number, starting at 0
 * @param size page size, from 1 to 100
 */
public record RoleSearchCriteria(String role, Instant createdBefore, int page, int size) {
}
