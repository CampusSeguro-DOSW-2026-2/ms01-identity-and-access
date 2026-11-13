-- Migración de roles y permisos flexibles (MS01)
--
-- Lleva una base de MS01 ya desplegada, creada con los scripts 001, 002 y 003 anteriores, al esquema nuevo:
--   * tabla roles con sus restricciones, la función protect_system_roles() y el trigger trg_protect_system_roles
--   * siembra de los ocho roles iniciales con la asignación de permisos de la Matriz Rol-Permiso
--   * columna usuarios.valido_desde (corte de sesión del usuario)
--   * usuario_rol.rol pasa de VARCHAR(20) con CHECK de ocho valores a VARCHAR(30) con clave foránea a roles(nombre)
--   * índices parciales idx_usuarios_valido_desde e idx_roles_valido_desde
--
-- No se usa en bases nuevas: 001 y 002 ya crean este esquema. Postgres no ejecuta los scripts de esta subcarpeta
-- al crear una base, se aplica a mano:
--   psql -h <host> -U <usuario> -d <base> -v ON_ERROR_STOP=1 -f database/migraciones/004_migracion_roles_permisos.sql
--
-- Corre en una sola transacción: si algo falla se revierte todo. Antes de aplicarla conviene guardar una copia:
--   pg_dump -h <host> -U <usuario> -Fc -f respaldo_antes_de_004.dump <base>

BEGIN;

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

ALTER TABLE usuarios ADD COLUMN valido_desde TIMESTAMPTZ DEFAULT NULL;

-- Los usuarios existentes siguen con los mismos nombres de rol, que ahora deben existir en roles
ALTER TABLE usuario_rol DROP CONSTRAINT chh_user_rol;

ALTER TABLE usuario_rol ALTER COLUMN rol TYPE VARCHAR(30);

ALTER TABLE usuario_rol
    ADD CONSTRAINT fk_usuario_rol_roles FOREIGN KEY (rol) REFERENCES roles(nombre) ON UPDATE CASCADE;

CREATE INDEX IF NOT EXISTS idx_usuarios_valido_desde ON usuarios (valido_desde) WHERE valido_desde IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_roles_valido_desde ON roles (valido_desde) WHERE valido_desde IS NOT NULL;

COMMIT;
