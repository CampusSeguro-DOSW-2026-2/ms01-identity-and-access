package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import java.util.Objects;

import org.springframework.beans.BeanWrapperImpl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida la restricción FieldsMatch comparando el valor de dos propiedades del objeto
 */
public class FieldsMatchImpl implements ConstraintValidator<FieldsMatch, Object> {

    private String field;
    private String matchField;
    private String message;

    /**
     * Guarda los nombres de las propiedades y el mensaje configurados en la anotación
     *
     * @param constraintAnnotation anotación con la configuración de la restricción
     */
    @Override
    public void initialize(FieldsMatch constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.matchField = constraintAnnotation.matchField();
        this.message = constraintAnnotation.message();
    }

    /**
     * Comprueba que las dos propiedades tengan el mismo valor, si alguna falta no se reporta aquí
     * porque la obligatoriedad se valida en cada propiedad
     *
     * @param value objeto que se valida
     * @param context contexto donde se registra el error asociado a la propiedad de confirmación
     * @return true si los valores coinciden o si alguno de los dos no fue enviado
     */
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        Object original = wrapper.getPropertyValue(field);
        Object confirmation = wrapper.getPropertyValue(matchField);
        if (original == null || confirmation == null) {
            return true;
        }
        if (Objects.equals(original, confirmation)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(matchField)
                .addConstraintViolation();
        return false;
    }
}
