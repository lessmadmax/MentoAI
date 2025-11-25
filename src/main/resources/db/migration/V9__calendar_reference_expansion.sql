ALTER TABLE calendar_events
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS job_posting_id BIGINT,
    ADD COLUMN IF NOT EXISTS recommend_log_id BIGINT;

ALTER TABLE calendar_events
    ADD CONSTRAINT fk_calendar_events_job_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_postings(job_id) ON DELETE SET NULL;

ALTER TABLE calendar_events
    ADD CONSTRAINT fk_calendar_events_recommend_log
        FOREIGN KEY (recommend_log_id) REFERENCES recommend_chat_logs(log_id) ON DELETE SET NULL;

UPDATE calendar_events
SET event_type = 'ACTIVITY',
    recommend_log_id = NULL
WHERE event_type IS NULL
  AND activity_id IS NOT NULL;

UPDATE calendar_events
SET event_type = 'CUSTOM'
WHERE event_type IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_calendar_event_user_activity_unique
    ON calendar_events(user_id, activity_id)
    WHERE activity_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_calendar_event_user_job_unique
    ON calendar_events(user_id, job_posting_id)
    WHERE job_posting_id IS NOT NULL;

