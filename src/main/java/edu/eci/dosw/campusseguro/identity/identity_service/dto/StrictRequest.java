package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Base de los cuerpos de petición que no admiten campos fuera del contrato (RG-04, additionalProperties
 * false en el OpenAPI), los campos desconocidos no hacen fallar la deserialización: se registran para que la
 * validación los rechace con HTTP 400 e indique cuáles son
 */
public abstract class StrictRequest {

    public static final String UNKNOWN_FIELD_MESSAGE = "El campo no está permitido.";

    public static final String UNKNOWN_FIELD_PLACEHOLDER = "campo_desconocido";

    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("^[A-Za-z0-9_\\-]{1,40}$");

    private final Set<String> unknownFields = new LinkedHashSet<>();

    /**
     * Registra una propiedad recibida que el contrato no define, el valor se descarta
     *
     * @param name nombre de la propiedad no definida en el contrato
     * @param value valor recibido para esa propiedad
     */
    @JsonAnySetter
    public void registerUnknownField(String name, Object value) {
        unknownFields.add(name);
    }

    /**
     * Devuelve los nombres de las propiedades recibidas que el contrato no define
     *
     * @return conjunto inmutable con los nombres, vacío si no se recibió ninguno
     */
    public Set<String> unknownFieldNames() {
        return Collections.unmodifiableSet(unknownFields);
    }

    /**
     * Mensaje de error de una propiedad no definida, las clases hijas pueden darle un mensaje propio a
     * las propiedades que quieren explicar
     *
     * @param fieldName nombre de la propiedad no definida
     * @return el mensaje de error de esa propiedad
     */
    public String messageForUnknownField(String fieldName) {
        return UNKNOWN_FIELD_MESSAGE;
    }

    /**
     * Construye un error por cada propiedad no definida, si el nombre recibido no es seguro de
     * devolver al cliente se reemplaza por un nombre genérico
     *
     * @return errores por campo, vacío si no se recibió ninguna propiedad desconocida
     */
    public List<ErrorResponse.ErrorDetail> unknownFieldErrors() {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        for (String name : unknownFields) {
            if (SAFE_FIELD_NAME.matcher(name).matches()) {
                errors.add(new ErrorResponse.ErrorDetail(name, messageForUnknownField(name)));
            } else {
                errors.add(new ErrorResponse.ErrorDetail(UNKNOWN_FIELD_PLACEHOLDER, UNKNOWN_FIELD_MESSAGE));
            }
        }
        return errors;
    }
}
