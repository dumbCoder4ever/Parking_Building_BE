-- =========================================================
-- Simulate session đỗ LÂU để verify fee tăng theo giờ
-- Database: railway (đổi nếu tên khác)
-- =========================================================

USE railway;

-- 1. Lấy session hiện tại của bạn
SELECT session_id, checkin_time, session_status, parking_duration, estimated_fee
FROM parking_sessions
WHERE session_id = '069df2f3-bdf2-4fc1-93ed-19b025e3e3f9';

-- 2. Lùi 4 tiếng (240 phút)
UPDATE parking_sessions
SET checkin_time = DATE_SUB(NOW(), INTERVAL 4 HOUR)
WHERE session_id = '069df2f3-bdf2-4fc1-93ed-19b025e3e3f9';

-- 3. Gọi API current session sau khi UPDATE, kiểm tra fee
--    parkingHours = 4:
--    - HOURLY:        20000 + 10000*3 = 50000
--    - TIERED Car:    tier2_price (vd 40000 vì 4h <= 6h)
--    - Nếu fee = 20000 -> bug vẫn còn

-- 4. Test thêm các mốc khác
UPDATE parking_sessions
SET checkin_time = DATE_SUB(NOW(), INTERVAL 10 HOUR)
WHERE session_id = '069df2f3-bdf2-4fc1-93ed-19b025e3e3f9';
-- parkingHours=10:
--   HOURLY:        20000 + 10000*9 = 110000
--   TIERED Car:    tier3_price (vd 60000 vì 10h <= 12h)

-- 5. Restore về 5 phút trước khi xong
UPDATE parking_sessions
SET checkin_time = DATE_SUB(NOW(), INTERVAL 5 MINUTE)
WHERE session_id = '069df2f3-bdf2-4fc1-93ed-19b025e3e3f9';

-- =========================================================
-- QUAN TRỌNG: Xem pricing_type thật trong DB
-- =========================================================
SELECT policy_id, vehicle_type_id, pricing_type, base_price, hourly_rate, max_hours,
       tier1_hours, tier1_price, tier2_hours, tier2_price,
       tier3_hours, tier3_price, tier4_hours, tier4_price, per_day_price, status
FROM pricing_policies
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
  AND status = 'ACTIVE';