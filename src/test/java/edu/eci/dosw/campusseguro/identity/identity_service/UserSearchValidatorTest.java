package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchCriteria;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchValidator;

/**
 * Validación de los filtros de búsqueda y de la paginación del listado de usuarios (formulario F-07,
 * Estándares de Integración, sección 6)
 */
class UserSearchValidatorTest {

    /**
     * Obtiene los errores por campo que produce una búsqueda inválida
     *
     * @param email filtro de correo
     * @param state filtro de estado
     * @param role filtro de rol
     * @param page número de página
     * @param size tamaño de página
     * @return los errores informados
     */
    private static List<ErrorResponse.ErrorDetail> errorsOf(String email, String state, String role, String page,
            String size) {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> UserSearchValidator.validate(email, state, role, page, size));
        return exception.getErrors();
    }

    /**
     * Sin parámetros se usan los valores por defecto
     */
    @Test
    void validate_withoutParameters_usesTheDefaults() {
        UserSearchCriteria criteria = UserSearchValidator.validate(null, null, null, null, null);

        assertNull(criteria.email());
        assertNull(criteria.state());
        assertNull(criteria.role());
        assertEquals(0, criteria.page());
        assertEquals(20, criteria.size());
    }

    /**
     * Los parámetros en blanco se tratan como ausentes
     */
    @Test
    void validate_blankParameters_areTreatedAsAbsent() {
        UserSearchCriteria criteria = UserSearchValidator.validate("  ", " ", "", " ", "");

        assertNull(criteria.email());
        assertNull(criteria.state());
        assertNull(criteria.role());
        assertEquals(0, criteria.page());
        assertEquals(20, criteria.size());
    }

    /**
     * El correo se recorta y se pasa a minúsculas
     */
    @Test
    void validate_email_isTrimmedAndLowercased() {
        UserSearchCriteria criteria = UserSearchValidator.validate("  ANA@EscuelaIng.edu.co ", null, null, null,
                null);

        assertEquals("ana@escuelaing.edu.co", criteria.email());
    }

    /**
     * Los filtros válidos se convierten a los valores del catálogo
     */
    @Test
    void validate_validFilters_areConverted() {
        UserSearchCriteria criteria = UserSearchValidator.validate(null, "INACTIVO", "BRIGADISTA", "2", "50");

        assertEquals(UserState.INACTIVO, criteria.state());
        assertEquals(UserRole.BRIGADISTA, criteria.role());
        assertEquals(2, criteria.page());
        assertEquals(50, criteria.size());
    }

    /**
     * El valor Todos equivale a no filtrar sin distinguir mayúsculas
     */
    @Test
    void validate_allValue_meansNoFilter() {
        UserSearchCriteria criteria = UserSearchValidator.validate(null, "Todos", "todos", null, null);

        assertNull(criteria.state());
        assertNull(criteria.role());
    }

    /**
     * Un estado fuera del catálogo se rechaza
     */
    @Test
    void validate_invalidState_isRejected() {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("estado", "Estado no válido.")),
                errorsOf(null, "PENDIENTE", null, null, null));
    }

    /**
     * Un rol fuera del catálogo se rechaza
     */
    @Test
    void validate_invalidRole_isRejected() {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("rol", "Rol no válido.")),
                errorsOf(null, null, "SUPERUSUARIO", null, null));
    }

    /**
     * Un correo con marcas de etiqueta se rechaza
     *
     * @param email filtro de correo que se prueba
     */
    @ParameterizedTest
    @ValueSource(strings = { "<script>", "ana>", "a<b" })
    void validate_emailWithTagMarks_isRejected(String email) {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("correo", "Búsqueda no válida.")),
                errorsOf(email, null, null, null, null));
    }

    /**
     * Un correo de más de 100 caracteres se rechaza
     */
    @Test
    void validate_emailLongerThan100_isRejected() {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("correo", "Búsqueda no válida.")),
                errorsOf("a".repeat(101), null, null, null, null));
    }

    /**
     * Un correo de exactamente 100 caracteres se acepta
     */
    @Test
    void validate_emailOf100_isAccepted() {
        assertEquals("a".repeat(100), UserSearchValidator.validate("a".repeat(100), null, null, null, null).email());
    }

    /**
     * Una página negativa o que no es número se rechaza
     *
     * @param page número de página que se prueba
     */
    @ParameterizedTest
    @ValueSource(strings = { "-1", "abc", "1.5" })
    void validate_invalidPage_isRejected(String page) {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("page", "Página no válida.")),
                errorsOf(null, null, null, page, null));
    }

    /**
     * Un tamaño fuera de 1 a 100 o que no es número se rechaza
     *
     * @param size tamaño de página que se prueba
     */
    @ParameterizedTest
    @ValueSource(strings = { "0", "101", "-5", "x" })
    void validate_invalidSize_isRejected(String size) {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("size", "Tamaño de página no válido (1 a 100).")),
                errorsOf(null, null, null, null, size));
    }

    /**
     * Los límites del tamaño de página se aceptan
     */
    @Test
    void validate_sizeBounds_areAccepted() {
        assertEquals(1, UserSearchValidator.validate(null, null, null, null, "1").size());
        assertEquals(100, UserSearchValidator.validate(null, null, null, null, "100").size());
    }

    /**
     * Varios errores se informan juntos, uno por campo y en orden alfabético
     */
    @Test
    void validate_severalErrors_areReportedTogetherSortedByField() {
        List<ErrorResponse.ErrorDetail> errors = errorsOf("<x>", "PENDIENTE", "NADA", "-1", "0");

        assertEquals(List.of("correo", "estado", "page", "rol", "size"),
                errors.stream().map(ErrorResponse.ErrorDetail::campo).toList());
    }
}
