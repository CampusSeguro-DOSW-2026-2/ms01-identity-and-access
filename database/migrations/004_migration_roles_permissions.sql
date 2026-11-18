-- Migration to flexible roles and permissions and to English object names (MS01)
--
-- Takes a deployed MS01 database, created with the previous 001, 002 and 003 scripts, to the new schema, which is
-- the same one that 001 and 002 create on a new database:
--   * tables, columns, constraints and indexes renamed to English (usuarios -> users, usuario_rol -> user_roles,
--     codigos_recuperacion -> password_reset_codes and their columns), the values stay as they are
--   * tables permissions, roles and role_permissions, with their constraints, functions and triggers (system role
--     protection, permission rules of each role and updated_at)
--   * seed of the 48-permission catalog, of the eight initial roles and of the Role-Permission Matrix assignment
--   * column users.valid_from (user session cut), status VARCHAR(20), second name and second lastname from one
--     character, email format CHECK and updated_at trigger
--   * the indexes of the status and of the email search (pg_trgm), created here when the database did not have them
--   * user_roles.rol (VARCHAR(20) with an eight-value CHECK) becomes user_roles.role_id with a foreign key to
--     roles(id)
--   * table password_reset_codes, created here when the database does not have the old codigos_recuperacion
--
-- It is not used on new databases: 001 and 002 already create this schema. Postgres does not run the scripts of
-- this subfolder when it creates a database, it is applied by hand:
--   psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -f database/migrations/004_migration_roles_permissions.sql
--
-- It runs in a single transaction: if anything fails, everything is rolled back. Before applying it, take a backup:
--   pg_dump -h <host> -U <user> -Fc -f backup_before_004.dump <database>

BEGIN;

-- Names in English for the tables, columns, constraints and indexes of the previous model
ALTER TABLE usuarios RENAME TO users;
ALTER TABLE users RENAME COLUMN primer_nombre TO first_name;
ALTER TABLE users RENAME COLUMN segundo_nombre TO second_name;
ALTER TABLE users RENAME COLUMN primer_apellido TO first_lastname;
ALTER TABLE users RENAME COLUMN segundo_apellido TO second_lastname;
ALTER TABLE users RENAME COLUMN correo TO email;
ALTER TABLE users RENAME COLUMN estado TO status;
ALTER TABLE users RENAME COLUMN creado_en TO created_at;
ALTER TABLE users RENAME COLUMN actualizado_en TO updated_at;
ALTER TABLE users RENAME CONSTRAINT usuarios_pkey TO users_pkey;
ALTER TABLE users RENAME CONSTRAINT uq_users_correo TO uq_users_email;
ALTER TABLE users RENAME CONSTRAINT chh_user_state TO chh_user_status;
ALTER TABLE users RENAME CONSTRAINT chh_correo_user TO chh_user_email;
ALTER INDEX IF EXISTS idx_usuarios_correo_trgm RENAME TO idx_users_email_trgm;
ALTER INDEX IF EXISTS idx_usuarios_estado RENAME TO idx_users_status;

ALTER TABLE usuario_rol RENAME TO user_roles;
ALTER TABLE user_roles RENAME COLUMN usuario_id TO user_id;
ALTER TABLE user_roles RENAME COLUMN asignado_en TO assigned_at;
ALTER TABLE user_roles RENAME CONSTRAINT fk_rol TO fk_user_roles_users;

-- The recovery code table exists only in databases created after the old 003 script
ALTER TABLE IF EXISTS codigos_recuperacion RENAME TO password_reset_codes;
ALTER TABLE IF EXISTS password_reset_codes RENAME COLUMN usuario_id TO user_id;
ALTER TABLE IF EXISTS password_reset_codes RENAME COLUMN codigo_hash TO code_hash;
ALTER TABLE IF EXISTS password_reset_codes RENAME COLUMN intentos TO attempts;
ALTER TABLE IF EXISTS password_reset_codes RENAME COLUMN expira_en TO expires_at;
ALTER TABLE IF EXISTS password_reset_codes RENAME COLUMN usado_en TO used_at;
ALTER TABLE IF EXISTS password_reset_codes RENAME COLUMN creado_en TO created_at;
ALTER TABLE IF EXISTS password_reset_codes RENAME CONSTRAINT codigos_recuperacion_pkey TO password_reset_codes_pkey;
ALTER TABLE IF EXISTS password_reset_codes RENAME CONSTRAINT fk_codigos_usuario TO fk_password_reset_codes_users;
ALTER TABLE IF EXISTS password_reset_codes
    RENAME CONSTRAINT chh_codigos_intentos TO chh_password_reset_codes_attempts;
ALTER INDEX IF EXISTS idx_codigos_usuario RENAME TO idx_password_reset_codes_user;

CREATE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE permissions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL,

    description VARCHAR(200) NOT NULL,

    CONSTRAINT uq_permissions_name UNIQUE (name),

    CONSTRAINT chh_permission_name
        CHECK (name ~ '^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$')
);

CREATE TABLE roles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(30) NOT NULL,

    description VARCHAR(200) NOT NULL,

    is_system BOOLEAN NOT NULL DEFAULT FALSE,

    valid_from TIMESTAMPTZ DEFAULT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_name UNIQUE (name),

    CONSTRAINT chh_role_name
        CHECK (name ~ '^[A-Z][A-Z0-9_]{1,29}$'),

    CONSTRAINT chh_role_name_reserved
        CHECK (name <> 'DETAILS'),

    CONSTRAINT chh_role_description
        CHECK (char_length(description) BETWEEN 3 AND 200)
);

CREATE FUNCTION protect_system_roles() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.is_system THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'System role % cannot be deleted', OLD.name
                USING ERRCODE = '23S01';
        END IF;
        IF NEW.name IS DISTINCT FROM OLD.name THEN
            RAISE EXCEPTION 'System role % cannot be renamed', OLD.name
                USING ERRCODE = '23S01';
        END IF;
        IF NEW.is_system IS DISTINCT FROM OLD.is_system THEN
            RAISE EXCEPTION 'System role % cannot change its system flag', OLD.name
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
    BEFORE DELETE OR UPDATE OF name, is_system ON roles
    FOR EACH ROW
    EXECUTE FUNCTION protect_system_roles();

CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE role_permissions (

    role_id UUID NOT NULL,

    permission_id UUID NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permissions_roles FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,

    CONSTRAINT fk_role_permissions_permissions FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- Permission rules of each role, checked when the transaction commits on the final state of every touched role:
--   * every role keeps ms01:profile:read and ms01:session:logout
--   * ESTUDIANTE and ADMIN also keep ms01:profile:write
--   * ms01:role:manage belongs only to ADMIN, which never loses it
CREATE FUNCTION check_role_permissions() RETURNS TRIGGER AS $$
DECLARE
    v_ids UUID[];
    v_id UUID;
    v_name VARCHAR(30);
BEGIN
    IF TG_TABLE_NAME = 'roles' THEN
        v_ids := ARRAY[NEW.id];
    ELSIF TG_OP = 'INSERT' THEN
        v_ids := ARRAY[NEW.role_id];
    ELSIF TG_OP = 'DELETE' THEN
        v_ids := ARRAY[OLD.role_id];
    ELSE
        v_ids := ARRAY[OLD.role_id, NEW.role_id];
    END IF;

    FOREACH v_id IN ARRAY v_ids LOOP
        SELECT name INTO v_name FROM roles WHERE id = v_id;
        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        IF (SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
            WHERE rp.role_id = v_id AND p.name IN ('ms01:profile:read', 'ms01:session:logout')) < 2 THEN
            RAISE EXCEPTION 'Role % must keep the permissions ms01:profile:read and ms01:session:logout', v_name
                USING ERRCODE = '23S01';
        END IF;

        IF v_name IN ('ESTUDIANTE', 'ADMIN') THEN
            IF NOT EXISTS (
                SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = v_id AND p.name = 'ms01:profile:write') THEN
                RAISE EXCEPTION 'Role % must keep the permission ms01:profile:write', v_name
                    USING ERRCODE = '23S01';
            END IF;
        END IF;

        IF v_name = 'ADMIN' THEN
            IF NOT EXISTS (
                SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = v_id AND p.name = 'ms01:role:manage') THEN
                RAISE EXCEPTION 'Role ADMIN must keep the permission ms01:role:manage'
                    USING ERRCODE = '23S01';
            END IF;
        ELSIF EXISTS (
                SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = v_id AND p.name = 'ms01:role:manage') THEN
            RAISE EXCEPTION 'The permission ms01:role:manage belongs only to the role ADMIN and the role % has it', v_name
                USING ERRCODE = '23S01';
        END IF;
    END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_check_role_permissions
    AFTER INSERT ON roles
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_role_permissions();

CREATE CONSTRAINT TRIGGER trg_check_role_permissions
    AFTER INSERT OR UPDATE OR DELETE ON role_permissions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_role_permissions();

-- The indexes of roles and role_permissions are created before the seed: Postgres does not allow creating an index on
-- a table with pending trigger events in the same transaction
CREATE INDEX IF NOT EXISTS idx_roles_valid_from ON roles (valid_from) WHERE valid_from IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission ON role_permissions (permission_id);

-- Permission catalog, one row per permission of the Role-Permission Matrix (one-time seed)
INSERT INTO permissions (name, description) VALUES
    ('ms01:password:change', 'Cambiar la propia contraseña'),
    ('ms01:profile:read', 'Consultar el propio perfil'),
    ('ms01:profile:write', 'Actualizar el propio perfil (nombres y apellidos)'),
    ('ms01:role:manage', 'Crear, renombrar, actualizar y borrar roles y asignarles permisos'),
    ('ms01:role:read_all', 'Consultar el catálogo completo de roles, el detalle de cada rol y el catálogo de permisos'),
    ('ms01:role:read_own', 'Consultar los propios roles y sus permisos'),
    ('ms01:session:logout', 'Cerrar sesión'),
    ('ms01:user:read', 'Listar, buscar y consultar usuarios'),
    ('ms01:user:roles', 'Agregar o retirar roles a usuarios'),
    ('ms01:user:status', 'Activar o inactivar usuarios'),
    ('ms02:building:write', 'Crear, modificar e inactivar campus, zonas abiertas, edificios, pisos, salidas, escaleras (sin su disponibilidad), imágenes y recomendaciones preventivas'),
    ('ms02:campus:read', 'Consultar campus, zonas abiertas, edificios, pisos, espacios, salidas, escaleras, zonas seguras (puntos de encuentro) e imágenes'),
    ('ms02:preventive_info:read', 'Consultar información preventiva por edificio o espacio'),
    ('ms02:qr:manage', 'Generar el código QR de espacios, salidas y zonas seguras'),
    ('ms02:route:read', 'Consultar la ruta de evacuación recomendada y listar o ver las rutas de evacuación'),
    ('ms02:route:write', 'Asociar espacios con rutas de evacuación y modificar su descripción o destino'),
    ('ms02:route_status:write', 'Cambiar el estado de una ruta (disponible, bloqueada, restringida) y la disponibilidad de una escalera (bloquearla o habilitarla)'),
    ('ms02:safe_zone:write', 'Gestionar zonas seguras y puntos de encuentro (un mismo recurso)'),
    ('ms02:space:write', 'Gestionar espacios'),
    ('ms03:alert:manage', 'Crear, programar, activar, segmentar, modificar y finalizar alertas de cualquier tipo'),
    ('ms03:alert:manage_drill', 'Igual que el anterior, solo para alertas de tipo SIMULACRO (originadas desde MS06)'),
    ('ms03:alert:read_history', 'Consultar el historial de alertas'),
    ('ms03:alert:receive', 'Recibir alertas y confirmar su recepción'),
    ('ms04:assistance:request', 'Solicitar asistencia desde el check-in'),
    ('ms04:checkin:read_own', 'Consultar el propio estado vigente'),
    ('ms04:checkin:write', 'Reportar y actualizar el propio estado y punto de encuentro'),
    ('ms04:dashboard:read', 'Ver consolidación por punto de encuentro, personas pendientes y dashboard'),
    ('ms04:history:read', 'Consultar el histórico de check-ins de un evento'),
    ('ms05:assistance:assign_to_drill', 'Asignar una solicitud a un brigadista y cerrarla para un simulacro'),
    ('ms05:assistance:assign_to_real', 'Asignar una solicitud a un brigadista y cerrarla para un evento real'),
    ('ms05:assistance:read', 'Consultar solicitudes de asistencia'),
    ('ms05:assistance:register', 'Registrar una solicitud de asistencia para alguien diferente a si mismo'),
    ('ms05:assistance:update_status', 'Cambiar el estado de atención (en atención, resuelta)'),
    ('ms05:brigadier:assign_to_drill', 'Asignar brigadistas a edificios, zonas o puntos de encuentro para simulacros'),
    ('ms05:brigadier:assign_to_real', 'Asignar brigadistas a edificios, zonas o puntos de encuentro para eventos reales'),
    ('ms05:incident:manage_own', 'Consultar, editar y eliminar incidentes propios'),
    ('ms05:incident:read', 'Consultar y dar seguimiento a incidentes'),
    ('ms05:incident:report', 'Registrar un incidente propio con descripción y evidencia'),
    ('ms05:incident:update', 'Actualizar un incidente'),
    ('ms05:panel:read_own', 'Consultar las propias zonas y tareas asignadas'),
    ('ms06:drill:compare_report', 'Comparar eventos con tipo SIMULACRO y consultar el informe final'),
    ('ms06:drill:evaluate', 'Evaluar un evento con tipo SIMULACRO'),
    ('ms06:drill:execute', 'Iniciar y terminar un evento con tipo SIMULACRO'),
    ('ms06:drill:manage', 'Crear y editar evento con tipo SIMULACRO, escenario y participantes'),
    ('ms06:drill:read', 'Consultar eventos con tipo SIMULACRO y su seguimiento'),
    ('ms06:real_event:compare_report', 'Comparar eventos reales (sin tipo SIMULACRO) y consultar el informe final'),
    ('ms06:real_event:evaluate', 'Evaluar un evento real (sin tipo SIMULACRO)'),
    ('ms06:real_event:execute', 'Iniciar y terminar un evento real (sin tipo SIMULACRO)'),
    ('ms06:real_event:manage', 'Crear y editar evento real (sin tipo SIMULACRO), escenario y participantes'),
    ('ms06:real_event:read', 'Consultar eventos reales (sin tipo SIMULACRO) y su seguimiento'),
    ('ms07:content:manage', 'Gestionar contenidos y la información que alimenta al tutor'),
    ('ms07:content:read', 'Consultar contenidos del centro de aprendizaje'),
    ('ms07:quiz:take', 'Realizar evaluaciones y consultar el propio progreso'),
    ('ms07:tutor:use', 'Usar el tutor CampusSeguro'),
    ('ms08:audit:read', 'Consultar la auditoría'),
    ('ms08:indicators:read', 'Consultar indicadores operativos e históricos');

-- Initial roles, only ESTUDIANTE and ADMIN are system roles
INSERT INTO roles (name, description, is_system) VALUES
    ('ESTUDIANTE', 'Estudiante de la comunidad universitaria', TRUE),
    ('PROFESOR', 'Profesor de la comunidad universitaria', FALSE),
    ('ADMINISTRATIVO', 'Personal administrativo de la comunidad universitaria', FALSE),
    ('BRIGADISTA', 'Brigadista de emergencias del campus', FALSE),
    ('COORDINADOR', 'Coordinador de emergencias: gestiona alertas, brigadistas y asistencia', FALSE),
    ('ORGANIZADOR', 'Organizador de simulacros y eventos de evacuación', FALSE),
    ('ADMIN', 'Administrador del sistema: gestiona usuarios, roles, campus y contenidos', TRUE),
    ('AUDITOR', 'Auditor: consulta históricos, indicadores y auditoría (solo lectura)', FALSE);

-- Initial permissions of each role, as the Role-Permission Matrix says
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('ESTUDIANTE',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms05:incident:manage_own',
            'ms05:incident:report',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use']),
    ('PROFESOR',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:manage',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms05:incident:manage_own',
            'ms05:incident:report',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use']),
    ('ADMINISTRATIVO',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms05:incident:manage_own',
            'ms05:incident:report',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use']),
    ('BRIGADISTA',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms04:dashboard:read',
            'ms05:assistance:read',
            'ms05:assistance:register',
            'ms05:assistance:update_status',
            'ms05:incident:manage_own',
            'ms05:incident:read',
            'ms05:incident:report',
            'ms05:incident:update',
            'ms05:panel:read_own',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use']),
    ('COORDINADOR',
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
            'ms05:assistance:assign_to_real',
            'ms05:assistance:read',
            'ms05:assistance:update_status',
            'ms05:brigadier:assign_to_real',
            'ms05:incident:manage_own',
            'ms05:incident:read',
            'ms05:incident:report',
            'ms05:incident:update',
            'ms06:real_event:compare_report',
            'ms06:real_event:evaluate',
            'ms06:real_event:execute',
            'ms06:real_event:manage',
            'ms06:real_event:read',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use',
            'ms08:indicators:read']),
    ('ORGANIZADOR',
     ARRAY[
            'ms01:password:change',
            'ms01:profile:read',
            'ms01:profile:write',
            'ms01:role:read_own',
            'ms01:session:logout',
            'ms02:campus:read',
            'ms02:preventive_info:read',
            'ms02:route:read',
            'ms03:alert:manage',
            'ms03:alert:manage_drill',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms04:dashboard:read',
            'ms04:history:read',
            'ms05:assistance:assign_to_drill',
            'ms05:assistance:read',
            'ms05:assistance:update_status',
            'ms05:brigadier:assign_to_drill',
            'ms05:incident:manage_own',
            'ms05:incident:read',
            'ms05:incident:report',
            'ms06:drill:compare_report',
            'ms06:drill:evaluate',
            'ms06:drill:execute',
            'ms06:drill:manage',
            'ms06:drill:read',
            'ms07:content:read',
            'ms07:quiz:take',
            'ms07:tutor:use',
            'ms08:indicators:read']),
    ('ADMIN',
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
            'ms03:alert:read_history',
            'ms05:incident:manage_own',
            'ms05:incident:report',
            'ms07:content:manage',
            'ms08:audit:read']),
    ('AUDITOR',
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
            'ms05:incident:manage_own',
            'ms05:incident:read',
            'ms06:drill:compare_report',
            'ms06:drill:read',
            'ms06:real_event:compare_report',
            'ms06:real_event:read',
            'ms08:audit:read',
            'ms08:indicators:read'])
) AS assignment (role_name, permission_names)
JOIN roles r ON r.name = assignment.role_name
JOIN permissions p ON p.name = ANY (assignment.permission_names);

-- users: session cut, second name and second lastname from one character, email format, status and updated_at
ALTER TABLE users ADD COLUMN valid_from TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE users DROP CONSTRAINT chh_user_second_name;

ALTER TABLE users
    ADD CONSTRAINT chh_user_second_name CHECK (char_length(second_name) BETWEEN 1 AND 30);

ALTER TABLE users DROP CONSTRAINT chh_user_second_lastname;

ALTER TABLE users
    ADD CONSTRAINT chh_user_second_lastname CHECK (char_length(second_lastname) BETWEEN 1 AND 30);

ALTER TABLE users
    ADD CONSTRAINT chh_user_email_format
        CHECK (email ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$');

ALTER TABLE users ALTER COLUMN status TYPE VARCHAR(20);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- user_roles: the existing role names become the foreign key to roles(id). If any user had a role that is not in
-- roles, role_id stays null and the whole migration is rolled back
ALTER TABLE user_roles ADD COLUMN role_id UUID;

UPDATE user_roles SET role_id = roles.id FROM roles WHERE roles.name = user_roles.rol;

ALTER TABLE user_roles ALTER COLUMN role_id SET NOT NULL;

ALTER TABLE user_roles DROP CONSTRAINT pk_rol;

ALTER TABLE user_roles DROP CONSTRAINT chh_user_rol;

ALTER TABLE user_roles DROP COLUMN rol;

ALTER TABLE user_roles ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id);

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_roles FOREIGN KEY (role_id) REFERENCES roles(id);

CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles (role_id, user_id);
CREATE INDEX IF NOT EXISTS idx_users_valid_from ON users (valid_from) WHERE valid_from IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);

-- The indexes of the previous model were renamed above; the email search index is created when the database did not
-- have it. It only speeds up the search, so if pg_trgm cannot be installed the migration goes on without it
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    CREATE INDEX IF NOT EXISTS idx_users_email_trgm ON users USING gin (email gin_trgm_ops);
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'The index idx_users_email_trgm was not created (%): run 002_index_schema.sql when pg_trgm is available', SQLERRM;
END;
$$;


-- Recovery code table for the databases that did not have it
CREATE TABLE IF NOT EXISTS password_reset_codes (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    code_hash VARCHAR(100) NOT NULL,

    attempts INTEGER NOT NULL DEFAULT 0,

    expires_at TIMESTAMPTZ NOT NULL,

    used_at TIMESTAMPTZ DEFAULT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_codes_users FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT chh_password_reset_codes_attempts
        CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_codes_user ON password_reset_codes (user_id, expires_at);

COMMIT;
