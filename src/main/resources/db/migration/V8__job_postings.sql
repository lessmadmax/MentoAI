CREATE TABLE job_postings (
    job_id          BIGSERIAL PRIMARY KEY,
    company_name    TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    rank            VARCHAR(50),
    job_sector      VARCHAR(100),
    employment_type VARCHAR(50),
    work_place      TEXT,
    career_level    VARCHAR(100),
    education_level VARCHAR(100),
    description     TEXT,
    requirements    TEXT,
    benefits        TEXT,
    link            TEXT,
    deadline        TIMESTAMPTZ,
    registered_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE job_posting_skills (
    job_id      BIGINT      NOT NULL REFERENCES job_postings(job_id) ON DELETE CASCADE,
    skill_name  TEXT        NOT NULL,
    proficiency VARCHAR(50),
    PRIMARY KEY(job_id, skill_name)
);

CREATE TABLE job_posting_roles (
    job_id         BIGINT NOT NULL REFERENCES job_postings(job_id) ON DELETE CASCADE,
    target_role_id TEXT   NOT NULL REFERENCES target_roles(role_id) ON DELETE CASCADE,
    relevance      DOUBLE PRECISION,
    PRIMARY KEY(job_id, target_role_id)
);

CREATE INDEX idx_job_postings_deadline ON job_postings(deadline);
CREATE INDEX idx_job_postings_sector ON job_postings(job_sector);
CREATE INDEX idx_job_postings_company ON job_postings(company_name);

