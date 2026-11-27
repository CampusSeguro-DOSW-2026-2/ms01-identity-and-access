package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Criteria of the user list already validated (form F-07)
 *
 * @param email partial search text by email in lowercase, null if it is not filtered by email
 * @param state status by which it is filtered, null if all are listed
 * @param role role by which it is filtered, null if all are listed
 * @param page page number, starting at 0
 * @param size page size, from 1 to 100
 */
public record UserSearchCriteria(String email, UserState state, String role, int page, int size) {
}
