package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleSearchCriteria;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchValidator;

/**
 * Tests of the validation of the internal query by role (RF-MS01-09)
 */
class RoleSearchValidatorTest {

        /**
     * With only the role the default page and size are used and there is no cut
     */
    @Test
    void validateByRole_onlyRole_usesTheDefaults() {
        RoleSearchCriteria criteria = UserSearchValidator.validateByRole(" BRIGADISTA ", null, null, null);

        assertEquals("BRIGADISTA", criteria.role());
        assertNull(criteria.createdBefore());
        assertEquals(UserSearchValidator.DEFAULT_PAGE, criteria.page());
        assertEquals(UserSearchValidator.DEFAULT_SIZE, criteria.size());
    }

        /**
     * All the valid parameters are converted
     */
    @Test
    void validateByRole_allParameters_areConverted() {
        RoleSearchCriteria criteria = UserSearchValidator.validateByRole("ESTUDIANTE", "2026-11-07T15:04:05Z",
                "3", "100");

        assertEquals(Instant.parse("2026-11-07T15:04:05Z"), criteria.createdBefore());
        assertEquals(3, criteria.page());
        assertEquals(100, criteria.size());
    }

        /**
     * An absent, empty role or one without the stored format is rejected, this parameter is required. Whether the role
     * exists is checked by the service
     *
     * @param role received role
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "brigadista", "SUPER-USUARIO", "1ROL", "R"})
    void validateByRole_missingOrInvalidRole_isRejected(String role) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> UserSearchValidator.validateByRole(role, null, null, null));

        assertEquals(List.of(new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_ROLE,
                UserSearchValidator.INVALID_ROLE_MESSAGE)), e.getErrors());
    }

        /**
     * A cut-off date that is not an ISO 8601 instant is rejected
     *
     * @param cutoff received date
     */
    @ParameterizedTest
    @ValueSource(strings = {"ayer", "2026-11-07", "2026-13-01T00:00:00Z"})
    void validateByRole_invalidCutoff_isRejected(String cutoff) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> UserSearchValidator.validateByRole("ESTUDIANTE", cutoff, null, null));

        assertEquals(List.of(new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_CREATED_BEFORE,
                UserSearchValidator.INVALID_CREATED_BEFORE_MESSAGE)), e.getErrors());
    }

        /**
     * The page and the size follow the rules of the list, with their messages
     */
    @Test
    void validateByRole_invalidPagination_reportsEachParameter() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> UserSearchValidator.validateByRole("ESTUDIANTE", null, "-1", "101"));

        assertEquals(List.of(
                new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_PAGE, UserSearchValidator.INVALID_PAGE_MESSAGE),
                new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_SIZE, UserSearchValidator.INVALID_SIZE_MESSAGE)),
                e.getErrors());
    }
}
