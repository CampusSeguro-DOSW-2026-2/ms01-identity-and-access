-- Cambio de permisos de MS01, generado por herramientas/matriz_permisos.py el 2026-11-18
--
-- Lleva una base existente (test y prod) desde la Matriz Rol-Permiso anterior a la actual, con SOLO lo que cambió:
-- no toca los roles creados desde la interfaz ni los demás cambios del administrador. Una base nueva no lo necesita:
-- 001 ya trae la Matriz actual. Reemplaza al cambio anterior (que queda en el historial de git).
--
-- Se puede aplicar más de una vez sin efecto, en una sola transacción; antes, un snapshot de RDS:
--   psql -h <host> -U <usuario> -d <base> -v ON_ERROR_STOP=1 -f database/maintenance/cambio_permisos.sql
--
--   Catálogo: 56 -> 58 permisos
--     + ms08:audit_drill:read
--     + ms08:audit_real_event:read
--     + ms08:indicators_drill:read
--     + ms08:indicators_real_event:read
--     - ms08:audit:read
--     - ms08:indicators:read
--   COORDINADOR: +ms08:audit_real_event:read +ms08:indicators_real_event:read -ms08:indicators:read
--   ORGANIZADOR: +ms08:audit_drill:read +ms08:indicators_drill:read -ms08:indicators:read
--   ADMIN: +ms08:audit_drill:read +ms08:audit_real_event:read +ms08:indicators_drill:read +ms08:indicators_real_event:read -ms08:audit:read
--   AUDITOR: +ms08:audit_drill:read +ms08:audit_real_event:read +ms08:indicators_drill:read +ms08:indicators_real_event:read -ms08:audit:read -ms08:indicators:read

BEGIN;

-- Corte de sesión de los roles que ganan o pierden un permiso, con la precisión de segundos que usa MS01
UPDATE roles SET valid_from = date_trunc('second', clock_timestamp())
WHERE name IN ('ADMIN', 'AUDITOR', 'COORDINADOR', 'ORGANIZADOR')
   OR id IN (SELECT rp.role_id FROM role_permissions rp
             JOIN permissions p ON p.id = rp.permission_id WHERE p.name IN ('ms08:audit:read', 'ms08:indicators:read'));

-- Permisos que salen de la Matriz: sus asignaciones se borran con ellos (ON DELETE CASCADE)
DELETE FROM permissions WHERE name IN ('ms08:audit:read', 'ms08:indicators:read');

-- Permisos nuevos y descripciones que cambiaron
INSERT INTO permissions (name, description) VALUES
    ('ms08:audit_drill:read', 'Consultar auditoría de simulacros'),
    ('ms08:audit_real_event:read', 'Consultar auditoría de eventos reales (con o sin evento_id)'),
    ('ms08:indicators_drill:read', 'Ver indicadores y comparaciones de simulacros'),
    ('ms08:indicators_real_event:read', 'Ver indicadores de eventos reales')
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description;

-- Permisos que la Matriz da a los roles iniciales (si el rol ya no existe, no hace nada)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('ADMIN', 'ms08:audit_drill:read'),
    ('ADMIN', 'ms08:audit_real_event:read'),
    ('ADMIN', 'ms08:indicators_drill:read'),
    ('ADMIN', 'ms08:indicators_real_event:read'),
    ('AUDITOR', 'ms08:audit_drill:read'),
    ('AUDITOR', 'ms08:audit_real_event:read'),
    ('AUDITOR', 'ms08:indicators_drill:read'),
    ('AUDITOR', 'ms08:indicators_real_event:read'),
    ('COORDINADOR', 'ms08:audit_real_event:read'),
    ('COORDINADOR', 'ms08:indicators_real_event:read'),
    ('ORGANIZADOR', 'ms08:audit_drill:read'),
    ('ORGANIZADOR', 'ms08:indicators_drill:read')
) AS cambio (role_name, permission_name)
JOIN roles r ON r.name = cambio.role_name
JOIN permissions p ON p.name = cambio.permission_name
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMIT;
