-- Drop legacy chat tables
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chat_sessions;

-- Logs for /recommend <-> Gemini interactions
CREATE TABLE recommend_chat_logs (
    log_id          BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    target_role_id  TEXT,
    user_query      TEXT,
    rag_prompt      TEXT,
    gemini_response TEXT,
    request_payload JSONB,
    response_payload JSONB,
    model_name      VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recommend_chat_logs_user ON recommend_chat_logs(user_id);
CREATE INDEX idx_recommend_chat_logs_created_at ON recommend_chat_logs(created_at);

