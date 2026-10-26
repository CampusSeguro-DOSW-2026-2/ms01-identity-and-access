package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;

/**
 * Catálogo de estados y códigos de error de los estándares de integración (sección 7.2)
 */
class ErrorCodeTest {

    /**
     * Cada código tiene el estado HTTP y el texto que define el catálogo
     *
     * @param name nombre de la constante
     * @param status estado HTTP esperado
     * @param code texto esperado del campo codigo
     */
    @ParameterizedTest
    @CsvSource({
            "VALIDATION,400,VALIDACION",
            "INVALID_CODE,400,CODIGO_INVALIDO",
            "UNAUTHENTICATED,401,NO_AUTENTICADO",
            "BAD_CREDENTIALS,401,CREDENCIALES_INCORRECTAS",
            "FORBIDDEN,403,PROHIBIDO",
            "INACTIVE_ACCOUNT,403,CUENTA_INACTIVA",
            "NOT_FOUND,404,NO_ENCONTRADO",
            "METHOD_NOT_ALLOWED,405,METODO_NO_PERMITIDO",
            "CONFLICT,409,CONFLICTO",
            "UNSUPPORTED_MEDIA_TYPE,415,TIPO_NO_SOPORTADO",
            "BUSINESS_RULE,422,REGLA_NEGOCIO",
            "INTERNAL_ERROR,500,ERROR_INTERNO"
    })
    void everyCode_hasTheStatusAndTheTextOfTheCatalog(String name, int status, String code) {
        ErrorCode errorCode = ErrorCode.valueOf(name);

        assertEquals(status, errorCode.getStatus());
        assertEquals(code, errorCode.getCode());
    }

    /**
     * El catálogo tiene exactamente los doce códigos que MS01 puede devolver y ninguno se repite
     */
    @Test
    void catalog_hasTwelveDifferentCodes() {
        Set<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertEquals(12, ErrorCode.values().length);
        assertEquals(12, codes.size());
    }

    /**
     * Todos los códigos usan solo mayúsculas y guion bajo, y tienen un mensaje con punto final
     */
    @Test
    void everyCode_followsTheFormatOfTheStandard() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertTrue(errorCode.getCode().matches("[A-Z]+(_[A-Z]+)*"), errorCode.getCode());
            assertFalse(errorCode.getMessage().isBlank(), errorCode.getCode());
            assertTrue(errorCode.getMessage().endsWith("."), errorCode.getCode());
        }
    }

    /**
     * Cada estado HTTP del catálogo se asocia con su código general, el primero declarado para ese estado
     *
     * @param status estado HTTP
     * @param name nombre de la constante esperada
     */
    @ParameterizedTest
    @CsvSource({
            "400,VALIDATION",
            "401,UNAUTHENTICATED",
            "403,FORBIDDEN",
            "404,NOT_FOUND",
            "405,METHOD_NOT_ALLOWED",
            "409,CONFLICT",
            "415,UNSUPPORTED_MEDIA_TYPE",
            "422,BUSINESS_RULE",
            "500,INTERNAL_ERROR"
    })
    void forStatus_returnsTheGeneralCodeOfTheStatus(int status, String name) {
        assertEquals(ErrorCode.valueOf(name), ErrorCode.forStatus(status));
    }

    /**
     * Un estado de cliente sin código propio se informa como validación
     *
     * @param status estado HTTP que el catálogo no contempla
     */
    @ParameterizedTest
    @ValueSource(ints = { 402, 406, 418, 429 })
    void forStatus_unlistedClientStatus_isValidation(int status) {
        assertEquals(ErrorCode.VALIDATION, ErrorCode.forStatus(status));
    }

    /**
     * Un estado de servidor sin código propio se informa como error interno
     *
     * @param status estado HTTP de servidor que el catálogo no contempla
     */
    @ParameterizedTest
    @ValueSource(ints = { 501, 502, 503, 504 })
    void forStatus_unlistedServerStatus_isInternalError(int status) {
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forStatus(status));
    }

    /**
     * Cada estado HTTP se asocia con el mensaje genérico del catálogo
     *
     * @param status estado HTTP
     * @param message mensaje esperado
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "400|Datos inválidos.",
            "401|No autenticado.",
            "403|No cuenta con los permisos suficientes para realizar esta operación.",
            "404|Recurso no encontrado.",
            "405|Método HTTP no permitido.",
            "415|Tipo de contenido no soportado.",
            "500|Error interno del servidor.",
            "502|Error interno del servidor."
    })
    void forStatus_messageIsTheGenericMessageOfTheCatalog(int status, String message) {
        assertEquals(message, ErrorCode.forStatus(status).getMessage());
    }

    /**
     * Los códigos de un mismo estado que se generan por una regla propia comparten el mensaje del código general
     */
    @Test
    void codesOfTheSameStatus_shareTheGeneralMessage() {
        assertEquals(ErrorCode.VALIDATION.getMessage(), ErrorCode.INVALID_CODE.getMessage());
        assertEquals(ErrorCode.UNAUTHENTICATED.getMessage(), ErrorCode.BAD_CREDENTIALS.getMessage());
        assertEquals(ErrorCode.FORBIDDEN.getMessage(), ErrorCode.INACTIVE_ACCOUNT.getMessage());
    }
}
