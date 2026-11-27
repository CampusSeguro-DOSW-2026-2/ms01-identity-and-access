package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import java.util.Objects;

import org.springframework.beans.BeanWrapperImpl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates the FieldsMatch constraint by comparing the value of two properties of the object
 */
public class FieldsMatchImpl implements ConstraintValidator<FieldsMatch, Object> {

    private String field;
    private String matchField;
    private String message;

        /**
     * Stores the property names and the message configured in the annotation
     *
     * @param constraintAnnotation annotation with the configuration of the constraint
     */
    @Override
    public void initialize(FieldsMatch constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.matchField = constraintAnnotation.matchField();
        this.message = constraintAnnotation.message();
    }

        /**
     * Checks that the two properties have the same value, if one is missing it is not reported here because the
     * requirement is validated in each property
     *
     * @param value object being validated
     * @param context context where the error associated with the confirmation property is recorded
     * @return true if the values match or if either of the two was not sent
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
