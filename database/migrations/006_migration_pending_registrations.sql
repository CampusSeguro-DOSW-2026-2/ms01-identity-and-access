-- Migration to the verification of the email in the registration (MS01, RF-MS01-01)
--
-- Takes a deployed MS01 database, with the schema of 001 and 002 or of migrations/004, to the new schema, which is
-- the same one that 001 and 002 create on a new database:
--   * table pending_registrations: registrations that wait for the verification of their email, with the email as the
--     key, the hashes of the password, of the code and of the registration token (only whoever made the registration
--     can confirm it), the attempts, the expiration, the instant of the last code and the counter of the daily limit
--     of codes
--   * index idx_pending_registrations_expires, used by the scheduled deletion of the expired registrations
--
-- It must be applied BEFORE deploying the version of MS01 that verifies the email: MS01 validates the schema when it
-- starts (ddl-auto: validate) and does not start without this table. It does not touch the existing users.
--
-- It is not used on new databases: 001 and 002 already create this schema. Postgres does not run the scripts of
-- this subfolder when it creates a database, it is applied by hand:
--   psql -h <host> -U <user> -d <database> -v ON_ERROR_STOP=1 -f database/migrations/006_migration_pending_registrations.sql
--
-- It runs in a single transaction: if anything fails, everything is rolled back. Before applying it, take a backup:
--   pg_dump -h <host> -U <user> -Fc -f backup_before_006.dump <database>

BEGIN;

CREATE TABLE pending_registrations (

    email VARCHAR(100) PRIMARY KEY,

    first_name VARCHAR(30) NOT NULL,

    second_name VARCHAR(30) DEFAULT NULL,

    first_lastname VARCHAR(30) NOT NULL,

    second_lastname VARCHAR(30) DEFAULT NULL,

    password_hash VARCHAR(100) NOT NULL,

    code_hash VARCHAR(100) NOT NULL,

    request_token_hash VARCHAR(64) NOT NULL,

    attempts INTEGER NOT NULL DEFAULT 0,

    expires_at TIMESTAMPTZ NOT NULL,

    code_sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    codes_sent INTEGER NOT NULL DEFAULT 1,

    window_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chh_pending_registrations_first_name
        CHECK (char_length(first_name) BETWEEN 2 AND 30),

    CONSTRAINT chh_pending_registrations_second_name
        CHECK (char_length(second_name) BETWEEN 1 AND 30),

    CONSTRAINT chh_pending_registrations_first_lastname
        CHECK (char_length(first_lastname) BETWEEN 2 AND 30),

    CONSTRAINT chh_pending_registrations_second_lastname
        CHECK (char_length(second_lastname) BETWEEN 1 AND 30),

    CONSTRAINT chh_pending_registrations_email
        CHECK (email = lower(email)),

    CONSTRAINT chh_pending_registrations_email_format
        CHECK (email ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'),

    CONSTRAINT chh_pending_registrations_attempts
        CHECK (attempts >= 0),

    CONSTRAINT chh_pending_registrations_codes_sent
        CHECK (codes_sent >= 1)
);

CREATE INDEX idx_pending_registrations_expires ON pending_registrations (expires_at);

COMMIT;
