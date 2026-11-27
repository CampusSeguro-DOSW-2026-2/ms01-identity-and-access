package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Requires that two properties of an object have the same value, for example a password and its confirmation (F-01,
 * F-05, F-06), the error is associated with the confirmation property
 */
@Documented
@Constraint(validatedBy = FieldsMatchImpl.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldsMatch {

        /**
     * Error message when the values do not match
     *
     * @return the error message
     */
    String message() default "Los valores no coinciden.";

        /**
     * Name of the property with the original value
     *
     * @return the name of the original property
     */
    String field();

        /**
     * Name of the property that must be identical to the original one and with which the error is associated
     *
     * @return the name of the confirmation property
     */
    String matchField();

        /**
     * Validation groups to which the constraint belongs
     *
     * @return the validation groups
     */
    Class<?>[] groups() default {};

        /**
     * Additional information for the validation clients
     *
     * @return the payloads of the constraint
     */
    Class<? extends Payload>[] payload() default {};
}
