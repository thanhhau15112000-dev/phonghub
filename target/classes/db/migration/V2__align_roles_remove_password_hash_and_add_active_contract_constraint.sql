-- =============================================================================
-- PHONGHUB - MIGRATION V2: ALIGN ROLES, SUPABASE AUTH SEAM & ACTIVE CONTRACT UNIQUE
-- =============================================================================

-- 1. Align user roles to ADMIN, STAFF, TECHNICIAN, TENANT
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'STAFF', 'TECHNICIAN', 'TENANT'));

-- 2. Remove password_hash (managed by Supabase Auth server-side) and add username alias
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(100) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Database-level invariant: A room cannot have two ACTIVE contracts at the same time
CREATE UNIQUE INDEX IF NOT EXISTS uk_contracts_active_room ON contracts (room_id) WHERE status = 'ACTIVE';
