-- =========================================================
-- Debug: Kiểm tra pricing_policies của vehicle type Car
-- Chạy trên Railway MySQL để xem policy thực tế trong DB
-- =========================================================

-- 1. Liệt kê TẤT CẢ pricing policies hiện tại
SELECT
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    max_hours,
    effective_from,
    effective_to,
    status,
    created_at
FROM pricing_policies
ORDER BY vehicle_type_id, status DESC, created_at DESC;

-- 2. Kiểm tra ACTIVE policy cho vehicle type Car (vehicleTypeId = 33333333-3333-3333-3333-333333333332)
SELECT
    policy_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    max_hours,
    effective_from,
    effective_to,
    status,
    NOW() AS db_now,
    (CASE
        WHEN status <> 'ACTIVE' THEN 'NOT_ACTIVE'
        WHEN effective_from IS NOT NULL AND effective_from > NOW() THEN 'NOT_YET_EFFECTIVE'
        WHEN effective_to IS NOT NULL AND effective_to < NOW() THEN 'EXPIRED'
        ELSE 'VALID'
    END) AS validity_check
FROM pricing_policies
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
ORDER BY created_at DESC;

-- 3. Tính expected fee cho parkingHours = 10 dựa trên policy thực tế:
--    fee = base_price + hourly_rate * (min(parkingHours, max_hours) - 1)
--    Với base=20000, hourly=10000, maxHours=24 -> fee = 20000 + 10000*9 = 110000
--    Nếu maxHours=1 -> fee = 20000 + 10000*0 = 20000 (match với response của bạn)
--    Nếu maxHours=24 nhưng response=20000 -> CACHE đang giữ policy cũ, cần restart backend

-- 4. Kiểm tra vehicles table xem plate đúng không
SELECT vehicle_id, plate_number, status, user_id, vehicle_type_id
FROM vehicles
WHERE plate_number LIKE '%A66666%';

-- 5. Kiểm tra parking_sessions ACTIVE hiện tại
SELECT
    session_id,
    user_id,
    vehicle_id,
    checkin_time,
    TIMESTAMPDIFF(MINUTE, checkin_time, NOW()) AS minutes_parked,
    TIMESTAMPDIFF(HOUR, checkin_time, NOW()) AS hours_parked,
    session_status,
    estimated_fee,
    total_fee
FROM parking_sessions
WHERE session_status = 'ACTIVE'
ORDER BY checkin_time DESC
LIMIT 5;