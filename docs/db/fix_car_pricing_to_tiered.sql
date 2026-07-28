-- ============================================================
-- FIX: Car pricing policy -> TIERED (bậc thang)
-- Issue: parkingHours=12 trả 20000 (HOURLY fallback) thay vì 60000 (TIERED)
-- Run time: < 1 giây, không ảnh hưởng sessions đang hoạt động
-- ============================================================

START TRANSACTION;

-- 1. Deactivate mọi policy HOURLY/DAILY cũ của Car
UPDATE pricing_policies
SET status = 'INACTIVE'
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332'
  AND status = 'ACTIVE';

-- 2. Insert policy TIERED mới cho Car
INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price, hourly_rate, overnight_fee, lost_ticket_fee,
    peak_hour_multiplier, max_daily_fee,
    tier1_hours, tier1_price,
    tier2_hours, tier2_price,
    tier3_hours, tier3_price,
    tier4_hours, tier4_price,
    per_day_price,
    max_hours,
    effective_from, effective_to,
    status, created_at
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333332',
    'Car Standard TIERED',
    'TIERED',
    NULL, NULL, NULL, NULL,
    1, 0,
    2,  20000.00,
    6,  40000.00,
    12, 60000.00,
    24, 100000.00,
    100000.00,
    24,
    NOW(), NULL,
    'ACTIVE', NOW()
);

COMMIT;

-- Verify
SELECT policy_id, policy_name, pricing_type, status, tier1_hours, tier1_price
FROM pricing_policies
WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332';
