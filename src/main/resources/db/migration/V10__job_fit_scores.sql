CREATE TABLE job_fit_scores (
    score_id        BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    job_id          BIGINT      NOT NULL REFERENCES job_postings(job_id) ON DELETE CASCADE,
    target_role_id  VARCHAR(255),
    job_url         TEXT,
    total_score     DOUBLE PRECISION NOT NULL,
    skill_fit       DOUBLE PRECISION,
    experience_fit  DOUBLE PRECISION,
    education_fit   DOUBLE PRECISION,
    evidence_fit    DOUBLE PRECISION,
    missing_skills  JSONB,
    recommendations JSONB,
    improvements    JSONB,
    requirements    JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_job_fit_scores_user_job ON job_fit_scores(user_id, job_id);
CREATE INDEX idx_job_fit_scores_created_at ON job_fit_scores(created_at DESC);

