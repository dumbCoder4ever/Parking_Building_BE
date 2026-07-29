-- ============================================================
-- REPLACE ALL PRICING WITH TIERED_HOURLY MODEL
-- Formula: basePrice for first 'includedHours', then hourlyRate × (hours - includedHours)
-- ============================================================
-- Vehicle Types:
-- Motorbike: 33333333-3333-3333-3333-333333333331
-- Car:       33333333-3333-3333-3333-333333333332
-- SUV:       33333333-3333-3333-3333-333333333333
-- Truck:     33333333-3333-3333-3333-333333333334
-- ============================================================

USE parking_db;

-- 1. Backup existing policies (optional - safety first)
CREATE TABLE IF NOT EXISTS pricing_policies_backup_20260729 AS 
SELECT * FROM pricing_policies WHERE status = 'ACTIVE';

-- 2. Add included_hours column if not exists
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'parking_db' 
    AND TABLE_NAME = 'pricing_policies' 
    AND COLUMN_NAME = 'included_hours'
);
SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE pricing_policies ADD COLUMN included_hours INT DEFAULT 3 AFTER hourly_rate',
    'SELECT "Column already exists"');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Deactivate all existing policies
SET SQL_SAFE_UPDATES = 0;
UPDATE pricing_policies SET status = 'INACTIVE' WHERE status = 'ACTIVE';
SET SQL_SAFE_UPDATES = 1;

-- 4. Insert 4 new TIERED_HOURLY policies
INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    included_hours,
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
    per_day_price,
    max_hours
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333331',
    'Motorbike TIERED_HOURLY',
    'TIERED_HOURLY',
    3000.00,   -- basePrice: 3 tiếng đầu = 3k
    3000.00,   -- hourlyRate: mỗi giờ tiếp = 3k
    3,         -- includedHours: 3 tiếng đầu
    NULL,      -- overnight_fee (reserved)
    NULL,      -- lost_ticket_fee (incident, not pricing)
    NULL,      -- peak_hour_multiplier
    NULL,      -- max_daily_fee
    NULL,      -- effective_from
    NULL,      -- effective_to
    'ACTIVE',
    NOW(),
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
);

INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    included_hours,
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
    per_day_price,
    max_hours
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333332',
    'Car TIERED_HOURLY',
    'TIERED_HOURLY',
    5000.00,   -- basePrice: 3 tiếng đầu = 5k
    5000.00,   -- hourlyRate: mỗi giờ tiếp = 5k
    3,         -- includedHours: 3 tiếng đầu
    NULL,      -- overnight_fee (reserved)
    NULL,      -- lost_ticket_fee (incident, not pricing)
    NULL,      -- peak_hour_multiplier
    NULL,      -- max_daily_fee
    NULL,      -- effective_from
    NULL,      -- effective_to
    'ACTIVE',
    NOW(),
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
);

INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    included_hours,
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
    per_day_price,
    max_hours
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333333',
    'SUV TIERED_HOURLY',
    'TIERED_HOURLY',
    7000.00,   -- basePrice: 3 tiếng đầu = 7k
    7000.00,   -- hourlyRate: mỗi giờ tiếp = 7k
    3,         -- includedHours: 3 tiếng đầu
    NULL,      -- overnight_fee (reserved)
    NULL,      -- lost_ticket_fee (incident, not pricing)
    NULL,      -- peak_hour_multiplier
    NULL,      -- max_daily_fee
    NULL,      -- effective_from
    NULL,      -- effective_to
    'ACTIVE',
    NOW(),
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
);

INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    included_hours,
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
    per_day_price,
    max_hours
) VALUES (
    UUID(),
    '33333333-3333-3333-3333-333333333334',
    'Truck TIERED_HOURLY',
    'TIERED_HOURLY',
    10000.00,  -- basePrice: 3 tiếng đầu = 10k
    10000.00,  -- hourlyRate: mỗi giờ tiếp = 10k
    3,         -- includedHours: 3 tiếng đầu
    NULL,      -- overnight_fee (reserved)
    NULL,      -- lost_ticket_fee (incident, not pricing)
    NULL,      -- peak_hour_multiplier
    NULL,      -- max_daily_fee
    NULL,      -- effective_from
    NULL,      -- effective_to
    'ACTIVE',
    NOW(),
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
);

-- 5. Verify: Check all 4 vehicle types have exactly 1 ACTIVE policy
SELECT 
    vt.type_name AS vehicle_type,
    p.pricing_type,
    p.base_price,
    p.hourly_rate,
    p.included_hours,
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
