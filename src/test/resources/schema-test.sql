-- Esquema para pruebas de integracion (H2 en modo PostgreSQL).
-- Refleja database/001_create_schema.sql + 002_migrate_pw_length.sql + 003_add_unique_email.sql

DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id    BIGSERIAL PRIMARY KEY,
    email VARCHAR(120) NOT NULL,
    pw    VARCHAR(255) NOT NULL,
    rol   VARCHAR(100) NOT NULL,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ckk_rol CHECK (rol IN (
        'ESTUDIANTE',
        'PROFESOR',
        'PERSONAL ADMINISTRATIVO',
        'BRIGADISTA',
        'COORDINADOR DE EMERGENCIAS',
        'ORGANIZADOR DE SIMULACROS',
        'AUDITOR'
    ))
);