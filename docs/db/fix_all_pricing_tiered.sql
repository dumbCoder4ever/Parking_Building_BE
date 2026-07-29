-- ============================================================
-- CHECK & FIX PRICING POLICIES FOR ALL 4 VEHICLE TYPES
-- ============================================================

USE parking_db;

-- 1. CHECK: Xem tất cả pricing policies hiện tại
SELECT
    vt.type_name AS vehicle_type,
    p.pricing_type,
    p.tier1_hours, p.tier1_price,
    p.tier2_hours, p.tier2_price,
    p.tier3_hours, p.tier3_price,
    p.tier4_hours, p.tier4_price,
    p.per_day_price,
    p.status,
    p.created_at
FROM vehicle_types vt
LEFT JOIN pricing_policies p ON p.vehicle_type_id = vt.vehicle_type_id
WHERE vt.vehicle_type_id IN (
    '33333333-3333-3333-3333-333333333331', -- MOTORBIKE
    '33333333-3333-3333-3333-333333333332', -- CAR
    '33333333-3333-3333-3333-333333333333', -- SUV
    '33333333-3333-3333-3333-333333333334'  -- TRUCK
)
ORDER BY vt.type_name, p.pricing_type;

-- 2. CHECK: Vehicle types nào có NHIỀU hơn 1 ACTIVE policy
SELECT
    vt.type_name,
    COUNT(p.policy_id) AS active_count,
    GROUP_CONCAT(CONCAT(p.pricing_type, ' (', p.policy_name, ')')) AS policies
FROM vehicle_types vt
LEFT JOIN pricing_policies p ON p.vehicle_type_id = vt.vehicle_type_id AND p.status = 'ACTIVE'
WHERE vt.vehicle_type_id IN (
    '33333333-3333-3333-3333-333333333331',
    '33333333-3333-3333-3333-333333333332',
    '33333333-3333-3333-3333-333333333333',
    '33333333-3333-3333-3333-333333333334'
)
GROUP BY vt.type_name
HAVING COUNT(p.policy_id) > 1;

-- 3. FIX: Deactivate duplicate policies (chỉ giữ TIERED ACTIVE)
-- Motorbike
UPDATE pricing_policies
SET status = 'INACTIVE'
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333331'
  AND UPPER(pricing_type) != 'TIERED'
  AND status = 'ACTIVE';

-- Car
UPDATE pricing_policies
SET status = 'INACTIVE'
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
  AND UPPER(pricing_type) != 'TIERED'
  AND status = 'ACTIVE';

-- SUV
UPDATE pricing_policies
SET status = 'INACTIVE'
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333333'
  AND UPPER(pricing_type) != 'TIERED'
  AND status = 'ACTIVE';

-- Truck
UPDATE pricing_policies
SET status = 'INACTIVE'
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333334'
  AND UPPER(pricing_type) != 'TIERED'
  AND status = 'ACTIVE';

-- 4. INSERT: Thêm pricing cho SUV (nếu chưa có)
INSERT INTO pricing_policies (
    policy_id, vehicle_type_id, policy_name, pricing_type,
    base_price, hourly_rate, overnight_fee, lost_ticket_fee,
    peak_hour_multiplier, max_daily_fee, effective_from, effective_to,
    status, created_at,
    tier1_hours, tier1_price, tier2_hours, tier2_price,
    tier3_hours, tier3_price, tier4_hours, tier4_price, per_day_price
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333333',
    'SUV Standard',
    'TIERED',
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL,
    'ACTIVE', NOW(),
    2, 25000.00,
    6,  50000.00,
    12, 75000.00,
    24, 120000.00,
    120000.00
)
ON DUPLICATE KEY UPDATE policy_name = 'SUV Standard';

-- 5. INSERT: Thêm pricing cho TRUCK (nếu chưa có)
INSERT INTO pricing_policies (
    policy_id, vehicle_type_id, policy_name, pricing_type,
    base_price, hourly_rate, overnight_fee, lost_ticket_fee,
    peak_hour_multiplier, max_daily_fee, effective_from, effective_to,
    status, created_at,
    tier1_hours, tier1_price, tier2_hours, tier2_price,
    tier3_hours, tier3_price, tier4_hours, tier4_price, per_day_price
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333334',
    'Truck Standard',
    'TIERED',
    NULL, NULL, NULL, NULL, NULL, NULL,
    NULL, NULL,
    'ACTIVE', NOW(),
    2, 35000.00,
    6,  70000.00,
    12, 100000.00,
    24, 150000.00,
    150000.00
)
ON DUPLICATE KEY UPDATE policy_name = 'Truck Standard';

-- 6. VERIFY: Kết quả sau khi fix
SELECT
    vt.type_name AS vehicle_type,
    p.pricing_type,
    p.tier1_price AS '2h',
    p.tier2_price AS '6h',
    p.tier3_price AS '12h',
    p.tier4_price AS '24h',
    p.per_day_price AS 'per_day',
    p.status
FROM vehicle_types vt
LEFT JOIN pricing_policies p ON p.vehicle_type_id = vt.vehicle_type_id AND p.status = 'ACTIVE'
WHERE vt.vehicle_type_id IN (
    '33333333-3333-3333-3333-333333333331',
    '33333333-3333-3333-3333-333333333332',
    '33333333-3333-3333-3333-333333333333',
    '33333333-3333-3333-3333-333333333334'
)
ORDER BY vt.type_name;
