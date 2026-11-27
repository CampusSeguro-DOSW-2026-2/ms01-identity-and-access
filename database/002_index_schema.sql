CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_users_email_trgm ON users USING gin (email gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles (role_id, user_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission ON role_permissions (permission_id);
CREATE INDEX IF NOT EXISTS idx_users_valid_from ON users (valid_from) WHERE valid_from IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_roles_valid_from ON roles (valid_from) WHERE valid_from IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_password_reset_codes_user ON password_reset_codes (user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_pending_registrations_expires ON pending_registrations (expires_at);
