CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_usuarios_correo_trgm ON usuarios USING gin (correo gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_usuarios_estado ON usuarios (estado);
CREATE INDEX IF NOT EXISTS idx_usuario_rol_rol ON usuario_rol (rol, usuario_id);