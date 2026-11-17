-- Maintenance of roles and permissions (MS01)
--
-- Leaves the catalog of permissions and the permissions of the eight Matrix roles exactly as the Role-Permission
-- Matrix says, so it is applied again whenever the Matrix gains permissions or assignments. It is generated from the
-- same Matrix as 001 and migrations/004, and it is run by hand, Postgres does not run it when it creates a database:
--   psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -f database/maintenance/005_maintenance_roles_permissions.sql
--
-- What it does, in a single transaction:
--   1. Takes away from every role all its permissions except the basic ones (ms01:profile:read, ms01:profile:write,
--      ms01:password:change, ms01:session:logout and ms01:role:read_own). ADMIN keeps ms01:role:manage. The roles that
--      are not in the Matrix, created through the API, keep only ms01:profile:read and ms01:session:logout, the
--      permissions that every role must have
--   2. Deletes from the catalog every permission that is not basic, except ms01:role:manage
--   3. Adds the whole catalog again, new permissions included, with the descriptions of the Matrix
--   4. Assigns the permissions to the eight Matrix roles as the Matrix says, and takes away from them any other
--   5. Cuts the sessions of all roles, so that the tokens issued before the change are rejected and each user logs in
--      again with the new permissions
--
-- The rules of the database (trigger check_role_permissions) are checked when the transaction commits, so the
-- intermediate states of the script are allowed. If the final state breaks a rule, everything is rolled back.
--
-- Before applying it, take a backup:
--   pg_dump -h <host> -U <user> -Fc -f backup_before_005.dump <database>

BEGIN;

-- Role-Permission Matrix
CREATE TEMPORARY TABLE matrix_assignments (
    role_name VARCHAR(30) NOT NULL,
    permission_name VARCHAR(100) NOT NULL
) ON COMMIT DROP;

INSERT INTO matrix_assignments (role_name, permission_name)
SELECT assignment.role_name, unnest(assignment.permission_names)
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
) AS assignment (role_name, permission_names);

-- 1. Permissions taken away from the roles that are not in the Matrix and from the Matrix roles
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE r.id = rp.role_id
  AND p.id = rp.permission_id
  AND r.name NOT IN ('ESTUDIANTE', 'PROFESOR', 'ADMINISTRATIVO', 'BRIGADISTA', 'COORDINADOR', 'ORGANIZADOR', 'ADMIN', 'AUDITOR')
  AND p.name NOT IN ('ms01:profile:read', 'ms01:session:logout');

DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE r.id = rp.role_id
  AND p.id = rp.permission_id
  AND r.name IN ('ESTUDIANTE', 'PROFESOR', 'ADMINISTRATIVO', 'BRIGADISTA', 'COORDINADOR', 'ORGANIZADOR', 'ADMIN', 'AUDITOR')
  AND p.name NOT IN ('ms01:profile:read', 'ms01:profile:write', 'ms01:password:change', 'ms01:session:logout', 'ms01:role:read_own')
  AND NOT (r.name = 'ADMIN' AND p.name = 'ms01:role:manage');

-- 2. Permissions deleted from the catalog, only the basic ones and ms01:role:manage stay
DELETE FROM permissions
WHERE name NOT IN ('ms01:profile:read', 'ms01:profile:write', 'ms01:password:change', 'ms01:session:logout', 'ms01:role:read_own', 'ms01:role:manage');

-- 3. Catalog added again, with the descriptions of the Matrix
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
    ('ms08:indicators:read', 'Consultar indicadores operativos e históricos')
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description;

-- 4. Permissions of the Matrix roles, as the Matrix says
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM matrix_assignments m
JOIN roles r ON r.name = m.role_name
JOIN permissions p ON p.name = m.permission_name
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE r.id = rp.role_id
  AND p.id = rp.permission_id
  AND r.name IN ('ESTUDIANTE', 'PROFESOR', 'ADMINISTRATIVO', 'BRIGADISTA', 'COORDINADOR', 'ORGANIZADOR', 'ADMIN', 'AUDITOR')
  AND NOT EXISTS (
      SELECT 1 FROM matrix_assignments m
      WHERE m.role_name = r.name AND m.permission_name = p.name);

-- 5. Session cut of all roles, with the same second precision that MS01 uses
UPDATE roles SET valid_from = date_trunc('second', clock_timestamp());

COMMIT;
