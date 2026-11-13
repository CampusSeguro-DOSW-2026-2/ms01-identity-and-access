CREATE TABLE roles (

    nombre VARCHAR(30) PRIMARY KEY,

    descripcion VARCHAR(200) NOT NULL,

    permisos TEXT[] NOT NULL,

    es_sistema BOOLEAN NOT NULL DEFAULT FALSE,

    valido_desde TIMESTAMPTZ DEFAULT NULL,

    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chh_role_name
        CHECK (nombre ~ '^[A-Z][A-Z0-9_]{1,29}$'),

    CONSTRAINT chh_role_name_reserved
        CHECK (nombre <> 'DETAILS'),

    CONSTRAINT chh_role_description
        CHECK (char_length(descripcion) BETWEEN 3 AND 200),

    CONSTRAINT chh_role_permissions
        CHECK (cardinality(permisos) >= 1),

    CONSTRAINT chh_role_manage_only_admin
        CHECK ((nombre = 'ADMIN') = ('ms01:role:manage' = ANY(permisos)))
);

CREATE FUNCTION protect_system_roles() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.es_sistema THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'El rol de sistema % no se puede eliminar', OLD.nombre
                USING ERRCODE = '23S01';
        END IF;
        IF NEW.nombre IS DISTINCT FROM OLD.nombre THEN
            RAISE EXCEPTION 'El rol de sistema % no se puede renombrar', OLD.nombre
                USING ERRCODE = '23S01';
        END IF;
        IF NEW.es_sistema IS DISTINCT FROM OLD.es_sistema THEN
            RAISE EXCEPTION 'El rol de sistema % no puede cambiar su marca de sistema', OLD.nombre
                USING ERRCODE = '23S01';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_system_roles
    BEFORE DELETE OR UPDATE OF nombre, es_sistema ON roles
    FOR EACH ROW
    EXECUTE FUNCTION protect_system_roles();

-- Roles iniciales con la asignación de permisos de la Matriz Rol-Permiso (siembra única)
INSERT INTO roles (nombre, descripcion, permisos, es_sistema) VALUES
    ('ESTUDIANTE',
     'Estudiante de la comunidad universitaria',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use'],
     TRUE),
    ('PROFESOR',
     'Profesor de la comunidad universitaria',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms05:incident:report',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use'],
     FALSE),
    ('ADMINISTRATIVO',
     'Personal administrativo de la comunidad universitaria',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use'],
     FALSE),
    ('BRIGADISTA',
     'Brigadista de emergencias del campus',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms04:dashboard:read',
            'ms05:assistance:read',
            'ms05:assistance:register',
            'ms05:assistance:update_status',
            'ms05:incident:read',
            'ms05:incident:report',
            'ms05:incident:update',
            'ms05:panel:read_own',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use'],
     FALSE),
    ('COORDINADOR',
     'Coordinador de emergencias: gestiona alertas, brigadistas y asistencia',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms02:route_status:write',
            'ms03:alert:manage',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms04:dashboard:read',
            'ms04:history:read',
            'ms05:assistance:assign',
            'ms05:assistance:read',
            'ms05:assistance:register',
            'ms05:assistance:update_status',
            'ms05:brigadier:assign',
            'ms05:incident:read',
            'ms05:incident:report',
            'ms05:incident:update',
            'ms06:drill:compare_report',
            'ms06:drill:evaluate',
            'ms06:drill:execute',
            'ms06:drill:manage',
            'ms06:drill:read',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use',
            'ms08:indicators:read'],
     FALSE),
    ('ORGANIZADOR',
     'Organizador de simulacros y eventos de evacuación',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:manage_drill',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms04:dashboard:read',
            'ms04:history:read',
            'ms06:drill:compare_report',
            'ms06:drill:evaluate',
            'ms06:drill:execute',
            'ms06:drill:manage',
            'ms06:drill:read',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use',
            'ms08:indicators:read'],
     FALSE),
    ('ADMIN',
     'Administrador del sistema: gestiona usuarios, roles, campus y contenidos',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:manage',
            'ms01:role:read_all',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms01:user:read',
            'ms01:user:roles',
            'ms01:user:status',
            'ms02:building:write',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:qr:manage',
            'ms02:route:read',
            'ms02:route:write',
            'ms02:route_status:write',
            'ms02:safe_zone:write',
            'ms02:space:write',
            'ms07:content:manage',
            'ms08:audit:read'],
     TRUE),
    ('AUDITOR',
     'Auditor: consulta históricos, indicadores y auditoría (solo lectura)',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms01:user:read',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:read_history',
            'ms04:dashboard:read',
            'ms04:history:read',
            'ms05:incident:read',
            'ms06:drill:compare_report',
            'ms06:drill:read',
            'ms08:audit:read',
            'ms08:indicators:read'],
     FALSE);

CREATE TABLE usuarios (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    primer_nombre VARCHAR(30) NOT NULL,
    
    segundo_nombre VARCHAR(30) DEFAULT NULL,
    
    primer_apellido VARCHAR(30) NOT NULL,
    
    segundo_apellido VARCHAR(30) DEFAULT NULL,
    
    correo VARCHAR(100) NOT NULL,
    
    password_hash VARCHAR(100) NOT NULL,
    
    estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
    
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    valido_desde TIMESTAMPTZ DEFAULT NULL,
    
    CONSTRAINT uq_users_correo UNIQUE(correo),

    CONSTRAINT chh_user_first_name
        CHECK (char_length(primer_nombre) BETWEEN 2 AND 30),
    
    CONSTRAINT chh_user_second_name
        CHECK (char_length(segundo_nombre) BETWEEN 2 AND 30),
    
    CONSTRAINT chh_user_first_lastname
        CHECK (char_length(primer_apellido) BETWEEN 2 AND 30),
    
    CONSTRAINT chh_user_second_lastname
        CHECK (char_length(segundo_apellido) BETWEEN 2 AND 30),

    CONSTRAINT chh_user_state
        CHECK (estado IN ('ACTIVO', 'INACTIVO')),

    CONSTRAINT chh_correo_user
        CHECK (correo = lower(correo))
);

CREATE TABLE usuario_rol(

    usuario_id UUID NOT NULL,

    rol VARCHAR(30) NOT NULL,

    asignado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_rol PRIMARY KEY (usuario_id, rol),

    CONSTRAINT fk_rol FOREIGN KEY (usuario_id) REFERENCES usuarios(id),

    CONSTRAINT fk_usuario_rol_roles FOREIGN KEY (rol) REFERENCES roles(nombre) ON UPDATE CASCADE
);
