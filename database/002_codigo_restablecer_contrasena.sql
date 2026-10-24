-- Tabla para los codigos de recuperacion de contrasena (RF-MS01-08)
-- Sigue las convenciones del esquema de usuarios: nombres en español, TIMESTAMPTZ y sufijo _en.

CREATE TABLE codigo_restablecer_contrasena (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    usuario_id UUID NOT NULL,

    codigo_hash VARCHAR(100) NOT NULL,

    vence_en TIMESTAMPTZ NOT NULL,

    usado_en TIMESTAMPTZ DEFAULT NULL,

    intentos INT NOT NULL DEFAULT 0,

    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_codigo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),

    CONSTRAINT chk_codigo_intentos CHECK (intentos >= 0)
);

CREATE INDEX idx_codigo_usuario ON codigo_restablecer_contrasena (usuario_id);
