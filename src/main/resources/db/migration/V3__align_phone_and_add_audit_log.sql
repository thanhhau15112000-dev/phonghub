-- =============================================================================
-- PHONGHUB - MIGRATION V3: NULLABLE PHONE & AUDIT LOG
-- =============================================================================

-- 1. Make phone optional in public.users to match domain model
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_phone ON users (phone) WHERE phone IS NOT NULL;

-- 2. Audit log table for tracking administrative events without storing credentials
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action VARCHAR(100) NOT NULL,
    actor_id UUID,
    target_type VARCHAR(100),
    target_id VARCHAR(100),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
