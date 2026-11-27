package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Base of the request bodies that do not accept fields outside the contract (RG-04, additionalProperties false in the
 * OpenAPI), unknown fields do not make the deserialization fail: they are recorded so that the validation rejects them
 * with HTTP 400 and says which ones they are
 */
public abstract class StrictRequest {

    public static final String UNKNOWN_FIELD_MESSAGE = "El campo no está permitido.";

    public static final String UNKNOWN_FIELD_PLACEHOLDER = "campo_desconocido";

    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("^[A-Za-z0-9_\\-]{1,40}$");

    private final Set<String> unknownFields = new LinkedHashSet<>();

        /**
     * Records a received property that the contract does not define, the value is discarded
     *
     * @param name name of the property not defined in the contract
     * @param value value received for that property
     */
    @JsonAnySetter
    public void registerUnknownField(String name, Object value) {
        unknownFields.add(name);
    }

        /**
     * Returns the names of the received properties that the contract does not define
     *
     * @return immutable set with the names, empty if none was received
     */
    public Set<String> unknownFieldNames() {
        return Collections.unmodifiableSet(unknownFields);
    }

        /**
     * Error message of an undefined property, the child classes can give their own message to the properties they want
     * to explain
     *
     * @param fieldName name of the undefined property
     * @return the error message of that property
     */
    public String messageForUnknownField(String fieldName) {
        return UNKNOWN_FIELD_MESSAGE;
    }

        /**
     * Builds one error for each undefined property, if the received name is not safe to return to the client it is
     * replaced by a generic name
     *
     * @return errors by field, empty if no unknown property was received
     */
    public List<ErrorResponse.ErrorDetail> unknownFieldErrors() {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        for (String name : unknownFields) {
            if (SAFE_FIELD_NAME.matcher(name).matches()) {
                errors.add(new ErrorResponse.ErrorDetail(name, messageForUnknownField(name)));
            } else {
                errors.add(new ErrorResponse.ErrorDetail(UNKNOWN_FIELD_PLACEHOLDER,
                        messageForUnknownField(UNKNOWN_FIELD_PLACEHOLDER)));
            }
        }
        return errors;
    }
}
