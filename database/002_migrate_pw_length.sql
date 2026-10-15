-- Migration: Increase password/hash column length to support BCrypt
ALTER TABLE users ALTER COLUMN pw TYPE VARCHAR(255);