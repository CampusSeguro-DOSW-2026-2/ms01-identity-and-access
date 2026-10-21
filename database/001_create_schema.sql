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

    rol VARCHAR(20) NOT NULL,

    asignado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_rol PRIMARY KEY (usuario_id, rol),

    CONSTRAINT fk_rol FOREIGN KEY (usuario_id) REFERENCES usuarios(id),

    CONSTRAINT chh_user_rol
        CHECK (rol IN ('ESTUDIANTE', 'PROFESOR', 'ADMINISTRATIVO','BRIGADISTA', 'COORDINADOR', 'ORGANIZADOR', 'ADMIN', 'AUDITOR'))
);
