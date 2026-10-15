-- Garantiza la unicidad del correo a nivel de base de datos (RN-MS01-MVP-01).
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);