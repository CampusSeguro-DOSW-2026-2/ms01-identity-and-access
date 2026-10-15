package edu.eci.dosw.login.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convierte {@link UserRol} al valor textual exacto definido por el constraint
 * ckk_rol de la tabla users (que utiliza espacios en lugar de guiones bajos).
 */
@Converter(autoApply = true)
public class UserRolConverter implements jakarta.persistence.AttributeConverter<UserRol, String> {

    @Override
    public String convertToDatabaseColumn(UserRol attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public UserRol convertToEntityAttribute(String dbData) {
        return UserRol.fromValue(dbData);
    }
}