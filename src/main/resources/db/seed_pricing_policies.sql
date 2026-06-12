-- =========================================================
-- SEED PRICING POLICIES — Tiered Pricing
-- =========================================================
-- Motorbike: ≤2h=5k, ≤6h=10k, ≤12h=15k, ≤24h=20k, +1day=20k
-- Car:      ≤2h=20k, ≤6h=40k, ≤12h=60k, ≤24h=100k, +1day=100k
-- =========================================================

USE parking_db;

-- MOTORBIKE PRICING
INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    overnight_fee,
    lost_ticket_fee,
    peak_hour_multiplier,
    max_daily_fee,
    effective_from,
    effective_to,
    status,
    created_at,
    tier1_hours, tier1_price,
    tier2_hours, tier2_price,
    tier3_hours, tier3_price,
    tier4_hours, tier4_price,
    per_day_price
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333331',
    'Motorbike Standard',
    'TIERED',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'ACTIVE',
    NOW(),
    2,  5000.00,
    6,  10000.00,
    12, 15000.00,
    24, 20000.00,
    20000.00
);

-- CAR PRICING
INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    overnight_fee,
    lost_ticket_fee,
    peak_hour_multiplier,
    max_daily_fee,
    effective_from,
    effective_to,
    status,
    created_at,
    tier1_hours, tier1_price,
    tier2_hours, tier2_price,
    tier3_hours, tier3_price,
    tier4_hours, tier4_price,
    per_day_price
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333332',
    'Car Standard',
    'TIERED',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'ACTIVE',
    NOW(),
    2,  20000.00,
    6,  40000.00,
    12, 60000.00,
    24, 100000.00,
    100000.00
);
