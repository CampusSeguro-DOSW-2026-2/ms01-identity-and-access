
CREATE TABLE codigos_recuperacion (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    usuario_id UUID NOT NULL,

    codigo_hash VARCHAR(100) NOT NULL,

    expira_en TIMESTAMPTZ NOT NULL,

    usado_en TIMESTAMPTZ DEFAULT NULL,

    intentos INT NOT NULL DEFAULT 0,

    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_codigo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),

    CONSTRAINT chk_codigo_intentos CHECK (intentos >= 0)
);

CREATE INDEX idx_codigos_usuario ON codigos_recuperacion (usuario_id, expira_en);
