package edu.eci.dosw.login.config;

import edu.eci.dosw.login.entity.UserRol;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Permite recibir el rol en parametros de consulta o ruta
 * (ej. {@code POST /api/v1/users/1/roles?rol=BRIGADISTA}).
 *
 * Acepta el nombre del enum de la API ("COORDINADOR_DE_EMERGENCIAS") y,
 * por conveniencia, el valor almacenado en base de datos
 * ("COORDINADOR DE EMERGENCIAS"). Es tolerante a mayusculas y espacios.
 *
 * Sin este converter, Spring solo aceptaria el nombre exacto del enum.
 */
@Component
public class UserRolRequestConverter implements Converter<String, UserRol> {

    @Override
    public UserRol convert(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        try {
            return UserRol.fromValue(source);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Rol no permitido: " + source);
        }
    }
}