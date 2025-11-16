CREATE TABLE IF NOT EXISTS user_profile_interest_domains (
    user_id bigint NOT NULL,
    domain_name text NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_profile_interest_domains
    ON user_profile_interest_domains (user_id, domain_name);

ALTER TABLE user_profile_interest_domains
    ADD CONSTRAINT fk_user_profile_interest_domains_profile
    FOREIGN KEY (user_id) REFERENCES user_profiles (user_id)
    ON DELETE CASCADE;


