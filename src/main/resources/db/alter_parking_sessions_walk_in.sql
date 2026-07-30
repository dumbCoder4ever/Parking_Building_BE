-- Run on Railway MySQL before deploying walk-in driver flow.
-- Safe to run once; skip if columns already exist.

ALTER TABLE parking_sessions
    ADD COLUMN user_id CHAR(36) NULL AFTER reservation_id;

ALTER TABLE parking_sessions
    ADD COLUMN checkin_type VARCHAR(30) NULL AFTER user_id;

ALTER TABLE parking_sessions
    ADD CONSTRAINT fk_parking_session_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL;

CREATE INDEX idx_parking_sessions_user_status
    ON parking_sessions(user_id, session_status);
