CREATE TABLE IF NOT EXISTS activity_target_roles (
    id BIGSERIAL PRIMARY KEY,
    activity_id BIGINT NOT NULL REFERENCES activities(activity_id) ON DELETE CASCADE,
    role_id TEXT NOT NULL REFERENCES target_roles(role_id) ON DELETE CASCADE,
    similarity_score DOUBLE PRECISION,
    matched_requirements JSONB,
    matched_preferences JSONB,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_activity_target_role_unique
    ON activity_target_roles(activity_id, role_id);

CREATE INDEX IF NOT EXISTS idx_activity_target_role_role
    ON activity_target_roles(role_id, similarity_score DESC);

