ALTER TABLE calendar_events
    ADD COLUMN IF NOT EXISTS event_title VARCHAR(255);

UPDATE calendar_events AS ce
SET event_title = a.title
FROM activities AS a
WHERE ce.activity_id = a.activity_id
  AND (ce.event_title IS NULL OR TRIM(ce.event_title) = '');

UPDATE calendar_events AS ce
SET event_title = CASE
        WHEN j.company_name IS NOT NULL AND j.title IS NOT NULL THEN j.company_name || ' - ' || j.title
        WHEN j.company_name IS NOT NULL THEN j.company_name
        ELSE j.title
    END
FROM job_postings AS j
WHERE ce.job_posting_id = j.job_id
  AND (ce.event_title IS NULL OR TRIM(ce.event_title) = '');

UPDATE calendar_events
SET event_title = '사용자 일정'
WHERE (event_title IS NULL OR TRIM(event_title) = '')
  AND (event_type IS NULL OR event_type = 'CUSTOM');

