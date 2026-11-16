BEGIN;

CREATE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE permisos (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    nombre VARCHAR(100) NOT NULL,

    descripcion VARCHAR(200) NOT NULL,

    CONSTRAINT uq_permisos_nombre UNIQUE (nombre),

    CONSTRAINT chh_permission_name
        CHECK (nombre ~ '^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$')
);

CREATE TABLE roles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    nombre VARCHAR(30) NOT NULL,

    descripcion VARCHAR(200) NOT NULL,

    es_sistema BOOLEAN NOT NULL DEFAULT FALSE,

    valido_desde TIMESTAMPTZ DEFAULT NULL,

    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_nombre UNIQUE (nombre),

    CONSTRAINT chh_role_name
        CHECK (nombre ~ '^[A-Z][A-Z0-9_]{1,29}$'),

    CONSTRAINT chh_role_name_reserved
        CHECK (nombre <> 'DETAILS'),

    CONSTRAINT chh_role_description
        CHECK (char_length(descripcion) BETWEEN 3 AND 200)
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

CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE rol_permiso (

    rol_id UUID NOT NULL,

    permiso_id UUID NOT NULL,

    CONSTRAINT pk_rol_permiso PRIMARY KEY (rol_id, permiso_id),

    CONSTRAINT fk_rol_permiso_roles FOREIGN KEY (rol_id) REFERENCES roles(id) ON DELETE CASCADE,

    CONSTRAINT fk_rol_permiso_permisos FOREIGN KEY (permiso_id) REFERENCES permisos(id) ON DELETE CASCADE
);

-- Reglas de los permisos de cada rol: al menos un permiso, ms01:role:manage solo en ADMIN (que nunca lo pierde)
-- y ms01:profile:read y ms01:profile:write siempre en ESTUDIANTE y ADMIN. Se evalúa al confirmar la transacción,
-- sobre el estado final de cada rol tocado
CREATE FUNCTION check_role_permissions() RETURNS TRIGGER AS $$
DECLARE
    v_ids UUID[];
    v_id UUID;
    v_nombre VARCHAR(30);
BEGIN
    IF TG_TABLE_NAME = 'roles' THEN
        v_ids := ARRAY[NEW.id];
    ELSIF TG_OP = 'INSERT' THEN
        v_ids := ARRAY[NEW.rol_id];
    ELSIF TG_OP = 'DELETE' THEN
        v_ids := ARRAY[OLD.rol_id];
    ELSE
        v_ids := ARRAY[OLD.rol_id, NEW.rol_id];
    END IF;

    FOREACH v_id IN ARRAY v_ids LOOP
        SELECT nombre INTO v_nombre FROM roles WHERE id = v_id;
        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM rol_permiso WHERE rol_id = v_id) THEN
            RAISE EXCEPTION 'El rol % debe tener al menos un permiso', v_nombre
                USING ERRCODE = '23S01';
        END IF;

        IF v_nombre = 'ADMIN' THEN
            IF NOT EXISTS (
                SELECT 1 FROM rol_permiso rp JOIN permisos p ON p.id = rp.permiso_id
                WHERE rp.rol_id = v_id AND p.nombre = 'ms01:role:manage') THEN
                RAISE EXCEPTION 'El rol ADMIN debe conservar el permiso ms01:role:manage'
                    USING ERRCODE = '23S01';
            END IF;
        ELSIF EXISTS (
                SELECT 1 FROM rol_permiso rp JOIN permisos p ON p.id = rp.permiso_id
                WHERE rp.rol_id = v_id AND p.nombre = 'ms01:role:manage') THEN
            RAISE EXCEPTION 'El permiso ms01:role:manage es exclusivo del rol ADMIN y el rol % lo tiene', v_nombre
                USING ERRCODE = '23S01';
        END IF;

        IF v_nombre IN ('ESTUDIANTE', 'ADMIN') THEN
            IF (SELECT count(*) FROM rol_permiso rp JOIN permisos p ON p.id = rp.permiso_id
                WHERE rp.rol_id = v_id AND p.nombre IN ('ms01:profile:read', 'ms01:profile:write')) < 2 THEN
                RAISE EXCEPTION 'El rol % debe conservar los permisos ms01:profile:read y ms01:profile:write', v_nombre
                    USING ERRCODE = '23S01';
            END IF;
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
    AFTER INSERT OR UPDATE OR DELETE ON rol_permiso
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_role_permissions();

CREATE TABLE usuarios (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    primer_nombre VARCHAR(30) NOT NULL,
    
    segundo_nombre VARCHAR(30) DEFAULT NULL,
    
    primer_apellido VARCHAR(30) NOT NULL,
    
    segundo_apellido VARCHAR(30) DEFAULT NULL,
    
    correo VARCHAR(100) NOT NULL,
    
    password_hash VARCHAR(100) NOT NULL,
    
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    valido_desde TIMESTAMPTZ DEFAULT NULL,
    
    CONSTRAINT uq_users_correo UNIQUE(correo),

    CONSTRAINT chh_user_first_name
        CHECK (char_length(primer_nombre) BETWEEN 2 AND 30),
    
    CONSTRAINT chh_user_second_name
        CHECK (char_length(segundo_nombre) BETWEEN 1 AND 30),
    
    CONSTRAINT chh_user_first_lastname
        CHECK (char_length(primer_apellido) BETWEEN 2 AND 30),
    
    CONSTRAINT chh_user_second_lastname
        CHECK (char_length(segundo_apellido) BETWEEN 1 AND 30),

    CONSTRAINT chh_user_state
        CHECK (estado IN ('ACTIVO', 'INACTIVO')),

    CONSTRAINT chh_correo_user
        CHECK (correo = lower(correo)),

    CONSTRAINT chh_correo_user_format
        CHECK (correo ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON usuarios
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE usuario_rol(

    usuario_id UUID NOT NULL,

    rol_id UUID NOT NULL,

    asignado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_rol PRIMARY KEY (usuario_id, rol_id),

    CONSTRAINT fk_rol FOREIGN KEY (usuario_id) REFERENCES usuarios(id),

    CONSTRAINT fk_usuario_rol_roles FOREIGN KEY (rol_id) REFERENCES roles(id)
);

-- Catálogo de permisos, uno por cada permiso de la Matriz Rol-Permiso (siembra única)
INSERT INTO permisos (nombre, descripcion) VALUES
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
    ('ms05:assistance:assign', 'Asignar una solicitud a un brigadista y cerrarla'),
    ('ms05:assistance:read', 'Consultar solicitudes de asistencia'),
    ('ms05:assistance:register', 'Registrar una solicitud de asistencia'),
    ('ms05:assistance:update_status', 'Cambiar el estado de atención (en atención, resuelta)'),
    ('ms05:brigadier:assign', 'Asignar brigadistas a edificios, zonas o puntos de encuentro'),
    ('ms05:incident:read', 'Consultar y dar seguimiento a incidentes'),
    ('ms05:incident:report', 'Registrar un incidente con descripción y evidencia'),
    ('ms05:incident:update', 'Actualizar un incidente'),
    ('ms05:panel:read_own', 'Consultar las propias zonas y tareas asignadas'),
    ('ms06:drill:compare_report', 'Comparar eventos y consultar el informe final'),
    ('ms06:drill:evaluate', 'Evaluar un evento'),
    ('ms06:drill:execute', 'Iniciar y terminar un evento'),
    ('ms06:drill:manage', 'Crear y editar evento, escenario y participantes'),
    ('ms06:drill:read', 'Consultar eventos y su seguimiento'),
    ('ms07:content:manage', 'Gestionar contenidos y la información que alimenta al tutor'),
    ('ms07:content:read', 'Consultar contenidos del centro de aprendizaje'),
    ('ms07:quiz:take', 'Realizar evaluaciones y consultar el propio progreso'),
    ('ms07:tutor:use', 'Usar el tutor CampusSeguro'),
    ('ms08:audit:read', 'Consultar la auditoría'),
    ('ms08:indicators:read', 'Consultar indicadores operativos e históricos');

-- Roles iniciales: solo ESTUDIANTE y ADMIN son de sistema
INSERT INTO roles (nombre, descripcion, es_sistema) VALUES
    ('ESTUDIANTE', 'Estudiante de la comunidad universitaria', TRUE),
    ('PROFESOR', 'Profesor de la comunidad universitaria', FALSE),
    ('ADMINISTRATIVO', 'Personal administrativo de la comunidad universitaria', FALSE),
    ('BRIGADISTA', 'Brigadista de emergencias del campus', FALSE),
    ('COORDINADOR', 'Coordinador de emergencias: gestiona alertas, brigadistas y asistencia', FALSE),
    ('ORGANIZADOR', 'Organizador de simulacros y eventos de evacuación', FALSE),
    ('ADMIN', 'Administrador del sistema: gestiona usuarios, roles, campus y contenidos', TRUE),
    ('AUDITOR', 'Auditor: consulta históricos, indicadores y auditoría (solo lectura)', FALSE);

-- Asignación inicial de permisos a cada rol, según la Matriz Rol-Permiso
INSERT INTO rol_permiso (rol_id, permiso_id)
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
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
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
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
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
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
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
            'ms03:alert:manage_drill',
            'ms03:alert:read_history',
            'ms03:alert:receive',
            'ms04:assistance:request',
            'ms04:checkin:read_own',
            'ms04:checkin:write',
            'ms04:dashboard:read',
            'ms04:history:read',
            'ms05:brigadier:assign',
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
            'ms05:incident:read',
            'ms06:drill:compare_report',
            'ms06:drill:read',
            'ms08:audit:read',
            'ms08:indicators:read'])
) AS asignacion (rol, permisos)
JOIN roles r ON r.nombre = asignacion.rol
JOIN permisos p ON p.nombre = ANY (asignacion.permisos);

COMMIT;
