CREATE TABLE IF NOT EXISTS codigos_recuperacion (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    usuario_id UUID NOT NULL,

    codigo_hash VARCHAR(100) NOT NULL,

    intentos INTEGER NOT NULL DEFAULT 0,

    expira_en TIMESTAMPTZ NOT NULL,

    usado_en TIMESTAMPTZ DEFAULT NULL,

    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_codigos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),

    CONSTRAINT chh_codigos_intentos
        CHECK (intentos >= 0)
);

CREATE INDEX IF NOT EXISTS idx_codigos_usuario ON codigos_recuperacion (usuario_id, expira_en);
