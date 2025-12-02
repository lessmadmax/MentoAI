-- 기본 사용자
INSERT INTO users (user_id, auth_provider, provider_user_id, email, name, nickname, profile_image_url, created_at, updated_at)
VALUES
    (1, 'GOOGLE', 'google-001', 'alice@mento.ai', '김앨리스', '앨리스', 'https://picsum.photos/200', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'GOOGLE', 'google-002', 'park@mento.ai', '박지훈', '지훈', 'https://picsum.photos/201', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 사용자 프로필
INSERT INTO user_profiles (user_id, university_name, university_grade, university_major, target_role_id, updated_at)
VALUES
    (1, 'KAIST', 3, '컴퓨터공학', 'backend-engineer', CURRENT_TIMESTAMP),
    (2, '연세대학교', 4, '산업공학', 'product-manager', CURRENT_TIMESTAMP);

-- 관심 도메인
INSERT INTO user_profile_interest_domains (user_id, domain_name) VALUES
    (1, '백엔드 개발'),
    (1, '인공지능'),
    (2, '프로덕트 기획');

-- 기술 스택
INSERT INTO user_profile_skills (user_id, skill_name, level) VALUES
    (1, 'Java', 'ADVANCED'),
    (1, 'Spring Boot', 'ADVANCED'),
    (1, 'MySQL', 'INTERMEDIATE'),
    (2, 'Figma', 'INTERMEDIATE'),
    (2, 'Communication', 'ADVANCED');

-- 타깃 롤
INSERT INTO target_roles (role_id, name, expected_seniority, created_at, updated_at)
VALUES
    ('backend-engineer', '백엔드 엔지니어', 'JUNIOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('product-manager', '프로덕트 매니저', 'ENTRY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO target_role_required_skills (role_id, skill_name, weight) VALUES
    ('backend-engineer', 'Java', 2.0),
    ('backend-engineer', 'Spring Boot', 1.8),
    ('backend-engineer', 'MySQL', 1.2),
    ('product-manager', 'Communication', 1.5),
    ('product-manager', 'Figma', 1.0);

INSERT INTO target_role_bonus_skills (role_id, skill_name, weight) VALUES
    ('backend-engineer', 'Docker', 0.8),
    ('backend-engineer', 'Kubernetes', 0.6),
    ('product-manager', 'SQL', 0.7);

INSERT INTO target_role_major_mapping (role_id, major, weight) VALUES
    ('backend-engineer', '컴퓨터공학', 3.0),
    ('backend-engineer', '소프트웨어공학', 2.0),
    ('product-manager', '산업공학', 2.5);

INSERT INTO target_role_recommended_certs (role_id, name) VALUES
    ('backend-engineer', '정보처리기사'),
    ('product-manager', 'PMP');

-- 활동 데이터
INSERT INTO activities (activity_id, title, summary, content, type, organizer, location, url, is_campus, status, vector_doc_id, published_at, created_at, updated_at)
VALUES
    (100, '클라우드 네이티브 해커톤', 'Spring Boot 및 Kubernetes 집중 경진대회', '실제 스타트업 과제를 해결하는 2주 프로젝트입니다.', 'CONTEST',
        'MentoAI', '서울', 'https://example.com/contest/100', FALSE, 'OPEN', NULL,
        TIMESTAMP '2024-11-01 09:00:00', TIMESTAMP '2024-10-20 09:00:00', TIMESTAMP '2024-10-25 09:00:00'),
    (101, 'Spring Boot 실전 스터디', 'REST API 설계를 학습하는 스터디', '주 2회 온라인으로 진행되며 미니 프로젝트를 완성합니다.', 'STUDY',
        'MentoAI', '온라인', 'https://example.com/study/101', TRUE, 'OPEN', NULL,
        TIMESTAMP '2024-09-01 09:00:00', TIMESTAMP '2024-08-20 09:00:00', TIMESTAMP '2024-08-25 09:00:00');

INSERT INTO activity_dates (date_id, activity_id, date_type, date_value) VALUES
    (1000, 100, 'APPLY_START', TIMESTAMP '2024-11-01 09:00:00'),
    (1001, 100, 'APPLY_END', TIMESTAMP '2024-11-15 18:00:00'),
    (1002, 100, 'EVENT_START', TIMESTAMP '2024-11-18 10:00:00'),
    (1003, 101, 'EVENT_START', TIMESTAMP '2024-09-05 20:00:00'),
    (1004, 101, 'EVENT_END', TIMESTAMP '2024-10-05 22:00:00');

-- 잡 포스팅 예시
INSERT INTO job_postings (job_id, company_name, title, rank, job_sector, employment_type, work_place, career_level, education_level,
                          description, requirements, benefits, link, deadline, registered_at, created_at, updated_at)
VALUES
    (500, 'MentoAI', '주니어 백엔드 엔지니어', '사원', '플랫폼개발', '정규직', '서울', '신입', '학사',
     '클라우드 기반 백엔드 서비스를 개발합니다.', 'Java/Spring 경험자 우대', '자율 복지비 제공', 'https://example.com/jobs/500',
     TIMESTAMP '2024-12-31 09:00:00', TIMESTAMP '2024-10-01 09:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 캘린더 이벤트
INSERT INTO calendar_events (event_id, user_id, event_type, activity_id, job_posting_id, recommend_log_id,
                             start_at, end_at, alert_minutes, created_at)
VALUES
    (1, 1, 'ACTIVITY', 100, NULL, NULL, TIMESTAMP '2024-11-10 09:00:00', TIMESTAMP '2024-11-10 20:00:00', 30, CURRENT_TIMESTAMP),
    (2, 1, 'JOB_POSTING', NULL, 500, NULL, TIMESTAMP '2024-12-15 10:00:00', TIMESTAMP '2024-12-15 11:00:00', 60, CURRENT_TIMESTAMP),
    (3, 1, 'CUSTOM', NULL, NULL, NULL, TIMESTAMP '2024-11-20 19:00:00', TIMESTAMP '2024-11-20 20:00:00', 15, CURRENT_TIMESTAMP);


