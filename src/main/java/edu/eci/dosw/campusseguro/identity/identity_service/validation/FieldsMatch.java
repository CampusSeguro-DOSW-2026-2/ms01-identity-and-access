package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Exige que dos propiedades de un objeto tengan el mismo valor, por ejemplo una contraseña y su
 * confirmación (F-01, F-05, F-06), el error se asocia a la propiedad de confirmación
 */
@Documented
@Constraint(validatedBy = FieldsMatchImpl.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldsMatch {

    /**
     * Mensaje de error cuando los valores no coinciden
     *
     * @return el mensaje de error
     */
    String message() default "Los valores no coinciden.";

    /**
     * Nombre de la propiedad con el valor original
     *
     * @return el nombre de la propiedad original
     */
    String field();

    /**
     * Nombre de la propiedad que debe ser idéntica a la original y a la que se asocia el error
     *
     * @return el nombre de la propiedad de confirmación
     */
    String matchField();

    /**
     * Grupos de validación a los que pertenece la restricción
     *
     * @return los grupos de validación
     */
    Class<?>[] groups() default {};

    /**
     * Información adicional para los clientes de la validación
     *
     * @return los payloads de la restricción
     */
    Class<? extends Payload>[] payload() default {};
}
