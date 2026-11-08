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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleSearchCriteria;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchValidator;

/**
 * Pruebas de la validación de la consulta interna por rol (RF-MS01-09)
 */
class RoleSearchValidatorTest {

    /**
     * Solo con el rol se usan la página y el tamaño por defecto y no hay corte
     */
    @Test
    void validateByRole_onlyRole_usesTheDefaults() {
        RoleSearchCriteria criteria = UserSearchValidator.validateByRole(" BRIGADISTA ", null, null, null);

        assertEquals(UserRole.BRIGADISTA, criteria.role());
        assertNull(criteria.createdBefore());
        assertEquals(UserSearchValidator.DEFAULT_PAGE, criteria.page());
        assertEquals(UserSearchValidator.DEFAULT_SIZE, criteria.size());
    }

    /**
     * Todos los parámetros válidos se convierten
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
     * Un rol ausente, vacío, fuera del catálogo o Todos se rechaza, este parámetro es obligatorio
     *
     * @param role rol recibido
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "brigadista", "SUPERUSUARIO", "TODOS"})
    void validateByRole_missingOrInvalidRole_isRejected(String role) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> UserSearchValidator.validateByRole(role, null, null, null));

        assertEquals(List.of(new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_ROLE,
                UserSearchValidator.INVALID_ROLE_MESSAGE)), e.getErrors());
    }

    /**
     * Una fecha de corte que no es un instante ISO 8601 se rechaza
     *
     * @param cutoff fecha recibida
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
     * La página y el tamaño siguen las reglas del listado, con sus mensajes
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
