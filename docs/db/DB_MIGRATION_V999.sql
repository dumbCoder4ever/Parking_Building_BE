-- =============================================================
-- V999__add_user_to_parking_sessions.sql
-- Migration: Bổ sung cột user_id vào parking_sessions
-- Purpose: Hỗ trợ walk-in check-in cho driver (xe đã đăng ký
--          nhưng không qua reservation), giúp query nhanh và
--          rõ ràng session thuộc về driver nào mà không phải
--          join qua vehicle -> vehicle.user.
-- Project: Parking_Building_BE
-- Author: Cursor Agent (Phase 1 — flow walk-in driver)
-- =============================================================

-- ON DELETE SET NULL: nếu driver bị xoá, giữ lại session lịch sử nhưng user_id = NULL.
-- Tránh FK violation khi xoá user (vd test cleanup hoặc GDPR).
ALTER TABLE parking_sessions
    ADD COLUMN user_id VARCHAR(36) NULL AFTER reservation_id,
    ADD CONSTRAINT fk_parking_session_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE SET NULL;

-- Index để query dashboard (activeSessions theo user) chạy nhanh.
CREATE INDEX idx_parking_sessions_user_status
    ON parking_sessions(user_id, session_status);

-- Rollback (nếu cần):
-- ALTER TABLE parking_sessions DROP FOREIGN KEY fk_parking_session_user;
-- DROP INDEX idx_parking_sessions_user_status ON parking_sessions;
-- ALTER TABLE parking_sessions DROP COLUMN user_id;
--
-- Lưu ý khi sửa FK: nếu recreate cần DROP trước:
-- ALTER TABLE parking_sessions DROP FOREIGN KEY fk_parking_session_user;
-- rồi ADD lại với ON DELETE SET NULL.
