package edu.eci.dosw.login.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Roles permitidos por el esquema de base de datos (constraint ckk_rol de la
 * tabla users, ver database/001_create_schema.sql).
 *
 * En la base de datos los valores usan espacios (ej. "PERSONAL ADMINISTRATIVO"),
 * mientras que en la API/JSON se usan nombres de enum (ej. "PERSONAL_ADMINISTRATIVO").
 * La conversion hacia/desde la BD la realiza {@link UserRolConverter}.
 */
public enum UserRol {

    ESTUDIANTE("Estudiante"),
    PROFESOR("Profesor"),
    PERSONAL_ADMINISTRATIVO("Personal Administrativo"),
    BRIGADISTA("Brigadista"),
    COORDINADOR_DE_EMERGENCIAS("Coordinador de Emergencias"),
    ORGANIZADOR_DE_SIMULACROS("Organizador de Simulacros"),
    AUDITOR("Auditor");

    private final String displayName;

    UserRol(String displayName) {
        this.displayName = displayName;
    }

    /** Valor legible para mostrar en la interfaz. */
    public String getDisplayName() {
        return displayName;
    }

    /** Valor exacto almacenado en la base de datos (con espacios). */
    public String getDbValue() {
        return name().replace('_', ' ');
    }

    /**
     * Construye el enum desde su nombre de API ("ESTUDIANTE") o desde el valor
     * de BD con espacios ("PERSONAL ADMINISTRATIVO"). Tolerante a mayusculas.
     */
    @JsonCreator
    public static UserRol fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UserRol.valueOf(value.trim().toUpperCase().replace(' ', '_'));
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}