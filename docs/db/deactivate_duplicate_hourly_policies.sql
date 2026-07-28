-- ============================================================
-- FIX: Deactivate duplicate HOURLY policy for Car (...332)
-- Issue: 2 ACTIVE policies for Car UUID 33333...332 (TIERED + HOURLY)
--        Repository picks created_at DESC = HOURLY (wrong)
--        => estimatedFee trả 20000 thay vì 60000 (tier3)
-- ============================================================

-- 1. Inspect trước khi xóa
SELECT
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    tier3_price,
    status,
    created_at
FROM pricing_policies
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
ORDER BY created_at DESC;

-- 2. Deactivate mọi HOURLY/DAILY/OVERNIGHT policy của Car (giữ TIERED ACTIVE)
UPDATE pricing_policies
SET status = 'INACTIVE'
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
  AND UPPER(pricing_type) != 'TIERED'
  AND status = 'ACTIVE';

-- 3. Verify chỉ còn 1 ACTIVE policy (TIERED) cho Car
SELECT
    policy_id,
    policy_name,
    pricing_type,
    status,
    tier3_price,
    max_hours
FROM pricing_policies
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
  AND status = 'ACTIVE';

-- 4. (Optional) Verify cả 4 vehicle types chỉ còn 1 ACTIVE mỗi loại
SELECT
    vt.type_name,
    vt.vehicle_type_id,
    COUNT(p.policy_id) AS active_count,
    MAX(CASE WHEN p.pricing_type = 'TIERED' THEN p.tier3_price END) AS tier3_price
FROM vehicle_types vt
LEFT JOIN pricing_policies p
    ON p.vehicle_type_id = vt.vehicle_type_id AND p.status = 'ACTIVE'
WHERE vt.type_name IN ('Motorbike', 'Car', 'SUV', 'Truck')
GROUP BY vt.type_name, vt.vehicle_type_id
HAVING COUNT(p.policy_id) > 1;
-- Expected: 0 rows nếu cleanup thành công
