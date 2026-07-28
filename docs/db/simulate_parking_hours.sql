-- =========================================================
-- Script test nhanh estimated fee theo thời gian thực
-- Cách dùng:
--   1. Lấy session_id ACTIVE của bạn:
--        SELECT session_id, checkin_time, vehicle_id, session_status
--        FROM parking_sessions
--        WHERE session_status = 'ACTIVE'
--        AND user_id = '<your-user-id>'
--        ORDER BY checkin_time DESC
--        LIMIT 1;
--
--   2. Set "lùi" checkin_time để simulate N tiếng đã đỗ:
--        UPDATE parking_sessions
--        SET checkin_time = DATE_SUB(NOW(), INTERVAL 9 HOUR)
--        WHERE session_id = '<your-session-id>';
--
--   3. Gọi API current session xem estimatedFee thay đổi.
--
--   4. Sau khi test xong, restore lại:
--        UPDATE parking_sessions
--        SET checkin_time = DATE_SUB(NOW(), INTERVAL 5 MINUTE)
--        WHERE session_id = '<your-session-id>';
-- =========================================================

-- Lấy session ACTIVE gần nhất của 1 user (thay USER_ID)
SELECT
    session_id,
    checkin_time,
    TIMESTAMPDIFF(MINUTE, checkin_time, NOW()) AS minutes_parked,
    TIMESTAMPDIFF(HOUR, checkin_time, NOW()) AS hours_parked,
    session_status,
    payment_status,
    total_fee
FROM parking_sessions
WHERE session_status = 'ACTIVE'
  AND user_id = 'PASTE_USER_ID_HERE'
ORDER BY checkin_time DESC
LIMIT 1;

-- Simulate đã đỗ 9 tiếng (540 phút)
UPDATE parking_sessions
SET checkin_time = DATE_SUB(NOW(), INTERVAL 540 MINUTE)
WHERE session_id = 'PASTE_SESSION_ID_HERE'
  AND session_status = 'ACTIVE';

-- Verify
SELECT
    session_id,
    checkin_time,
    TIMESTAMPDIFF(MINUTE, checkin_time, NOW()) AS minutes_parked
FROM parking_sessions
WHERE session_id = 'PASTE_SESSION_ID_HERE';

-- Sau khi test xong, restore lại về "vừa check-in 5 phút trước"
UPDATE parking_sessions
SET checkin_time = DATE_SUB(NOW(), INTERVAL 5 MINUTE)
WHERE session_id = 'PASTE_SESSION_ID_HERE'
  AND session_status = 'ACTIVE';
