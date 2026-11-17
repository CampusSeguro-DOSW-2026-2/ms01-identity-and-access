package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchCriteria;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchValidator;

/**
 * Validation of the search filters and the pagination of the user list (form F-07, Integration Standards, section 6)
 */
class UserSearchValidatorTest {

        /**
     * Gets the errors by field produced by an invalid search
     *
     * @param email email filter
     * @param state status filter
     * @param role role filter
     * @param page page number
     * @param size page size
     * @return the reported errors
     */
    private static List<ErrorResponse.ErrorDetail> errorsOf(String email, String state, String role, String page,
            String size) {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> UserSearchValidator.validate(email, state, role, page, size));
        return exception.getErrors();
    }

        /**
     * Without parameters the default values are used
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
     * The blank parameters are treated as absent
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
     * The email is trimmed and converted to lowercase
     */
    @Test
    void validate_email_isTrimmedAndLowercased() {
        UserSearchCriteria criteria = UserSearchValidator.validate("  ANA@EscuelaIng.edu.co ", null, null, null,
                null);

        assertEquals("ana@escuelaing.edu.co", criteria.email());
    }

        /**
     * The valid filters are converted to the values of the catalog
     */
    @Test
    void validate_validFilters_areConverted() {
        UserSearchCriteria criteria = UserSearchValidator.validate(null, "INACTIVO", "BRIGADISTA", "2", "50");

        assertEquals(UserState.INACTIVO, criteria.state());
        assertEquals("BRIGADISTA", criteria.role());
        assertEquals(2, criteria.page());
        assertEquals(50, criteria.size());
    }

        /**
     * The value Todos is equivalent to not filtering regardless of case
     */
    @Test
    void validate_allValue_meansNoFilter() {
        UserSearchCriteria criteria = UserSearchValidator.validate(null, "Todos", "todos", null, null);

        assertNull(criteria.state());
        assertNull(criteria.role());
    }

        /**
     * A status outside the catalog is rejected
     */
    @Test
    void validate_invalidState_isRejected() {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("estado", "Estado no válido.")),
                errorsOf(null, "PENDIENTE", null, null, null));
    }

        /**
     * A role without the stored format is rejected, whether the role exists is checked by the service
     */
    @Test
    void validate_invalidRole_isRejected() {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("rol", "Rol no válido.")),
                errorsOf(null, null, "SUPER-USUARIO", null, null));
    }

        /**
     * An email with tag marks is rejected
     *
     * @param email email filter that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "<script>", "ana>", "a<b" })
    void validate_emailWithTagMarks_isRejected(String email) {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("correo", "Búsqueda no válida.")),
                errorsOf(email, null, null, null, null));
    }

        /**
     * An email of more than 100 characters is rejected
     */
    @Test
    void validate_emailLongerThan100_isRejected() {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("correo", "Búsqueda no válida.")),
                errorsOf("a".repeat(101), null, null, null, null));
    }

        /**
     * An email of exactly 100 characters is accepted
     */
    @Test
    void validate_emailOf100_isAccepted() {
        assertEquals("a".repeat(100), UserSearchValidator.validate("a".repeat(100), null, null, null, null).email());
    }

        /**
     * A negative page or one that is not a number is rejected
     *
     * @param page page number that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "-1", "abc", "1.5" })
    void validate_invalidPage_isRejected(String page) {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("page", "Página no válida.")),
                errorsOf(null, null, null, page, null));
    }

        /**
     * A size outside 1 to 100 or that is not a number is rejected
     *
     * @param size page size that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "0", "101", "-5", "x" })
    void validate_invalidSize_isRejected(String size) {
        assertEquals(List.of(new ErrorResponse.ErrorDetail("size", "Tamaño de página no válido (1 a 100).")),
                errorsOf(null, null, null, null, size));
    }

        /**
     * The limits of the page size are accepted
     */
    @Test
    void validate_sizeBounds_areAccepted() {
        assertEquals(1, UserSearchValidator.validate(null, null, null, null, "1").size());
        assertEquals(100, UserSearchValidator.validate(null, null, null, null, "100").size());
    }

        /**
     * Several errors are reported together, one per field and in alphabetical order
     */
    @Test
    void validate_severalErrors_areReportedTogetherSortedByField() {
        List<ErrorResponse.ErrorDetail> errors = errorsOf("<x>", "PENDIENTE", "NADA-1", "-1", "0");

        assertEquals(List.of("correo", "estado", "page", "rol", "size"),
                errors.stream().map(ErrorResponse.ErrorDetail::campo).toList());
    }
}
