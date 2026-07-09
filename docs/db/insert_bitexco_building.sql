-- =========================================================
-- INSERT NEW BUILDING: BITEXCO PARKING
-- Run in Railway MySQL Query Editor (after railway_migration.sql)
-- =========================================================
USE railway;

-- =========================================================
-- 1. INSERT BUILDING: BITEXCO
-- =========================================================
INSERT INTO buildings (
    building_id,
    building_name,
    address,
    total_floors,
    operating_start_time,
    operating_end_time,
    contact_number,
    status
) VALUES (
    'cccc1111-cccc-1111-cccc-111111111111',
    'Bitexco Parking Building',
    'Bitexco Financial Tower, District 1, Ho Chi Minh City',
    4,
    '06:00:00',
    '23:00:00',
    '0909111222',
    'ACTIVE'
);

-- =========================================================
-- 2. INSERT 4 FLOORS
--    - Floor 1: Motorbike, 60 slots
--    - Floor 2: Car, 80 slots
--    - Floor 3: SUV, 80 slots
--    - Floor 4: Truck, 80 slots
-- =========================================================
INSERT INTO floors (
    floor_id,
    building_id,
    vehicle_type_id,
    floor_name,
    floor_level,
    max_capacity,
    status
) VALUES
('cccc1111-1111-1111-1111-111111111101', 'cccc1111-cccc-1111-cccc-111111111111', '33333333-3333-3333-3333-333333333331', 'Bitexco Floor 1 - Motorbike', 1, 60,  'ACTIVE'),
('cccc1111-1111-1111-1111-111111111102', 'cccc1111-cccc-1111-cccc-111111111111', '33333333-3333-3333-3333-333333333332', 'Bitexco Floor 2 - Car',       2, 80,  'ACTIVE'),
('cccc1111-1111-1111-1111-111111111103', 'cccc1111-cccc-1111-cccc-111111111111', '33333333-3333-3333-3333-333333333333', 'Bitexco Floor 3 - SUV',       3, 80,  'ACTIVE'),
('cccc1111-1111-1111-1111-111111111104', 'cccc1111-cccc-1111-cccc-111111111111', '33333333-3333-3333-3333-333333333334', 'Bitexco Floor 4 - Truck',     4, 80,  'ACTIVE');

-- =========================================================
-- 3. INSERT 1 ZONE PER FLOOR (4 zones total, 10 slots each)
-- =========================================================
INSERT INTO zones (
    zone_id,
    floor_id,
    zone_name,
    max_capacity,
    status
) VALUES
('cccc2222-2222-2222-2222-222222222201', 'cccc1111-1111-1111-1111-111111111101', 'Zone A - Bitexco F1', 10, 'ACTIVE'),
('cccc2222-2222-2222-2222-222222222202', 'cccc1111-1111-1111-1111-111111111102', 'Zone A - Bitexco F2', 10, 'ACTIVE'),
('cccc2222-2222-2222-2222-222222222203', 'cccc1111-1111-1111-1111-111111111103', 'Zone A - Bitexco F3', 10, 'ACTIVE'),
('cccc2222-2222-2222-2222-222222222204', 'cccc1111-1111-1111-1111-111111111104', 'Zone A - Bitexco F4', 10, 'ACTIVE');

-- =========================================================
-- 4. INSERT 10 PARKING SLOTS PER ZONE (40 slots total)
--    Use a recursive CTE (MySQL 8+) to generate 1..10
-- =========================================================
INSERT INTO parking_slots (slot_id, zone_id, slot_name, slot_status)
SELECT
    UUID(),
    z.zone_id,
    CONCAT('BITEXCO-F', f.floor_level, '-', LPAD(n.n, 2, '0')) AS slot_name,
    'AVAILABLE' AS slot_status
FROM zones z
JOIN floors f ON z.floor_id = f.floor_id
JOIN (
    WITH RECURSIVE nums AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM nums WHERE n < 10
    )
    SELECT n FROM nums
) n
WHERE z.zone_id IN (
    'cccc2222-2222-2222-2222-222222222201',
    'cccc2222-2222-2222-2222-222222222202',
    'cccc2222-2222-2222-2222-222222222203',
    'cccc2222-2222-2222-2222-222222222204'
);

-- =========================================================
-- 5. ASSIGN STAFF (staff1) TO BITEXCO
-- =========================================================
INSERT INTO building_staff (assignment_id, building_id, user_id) VALUES
('cccc3333-3333-3333-3333-333333333301', 'cccc1111-cccc-1111-cccc-111111111111', '33333333-3333-3333-3333-333333333333');

-- =========================================================
-- VERIFICATION QUERIES
-- =========================================================
SELECT 'Bitexco building inserted!' AS status;

SELECT
    b.building_id,
    b.building_name,
    b.address,
    b.total_floors,
    b.status
FROM buildings b
WHERE b.building_id = 'cccc1111-cccc-1111-cccc-111111111111';

SELECT
    f.floor_level,
    f.floor_name,
    vt.type_name AS vehicle_type,
    f.max_capacity AS floor_capacity,
    z.zone_name,
    z.max_capacity AS zone_capacity,
    (SELECT COUNT(*) FROM parking_slots ps WHERE ps.zone_id = z.zone_id) AS total_slots
FROM floors f
JOIN vehicle_types vt ON f.vehicle_type_id = vt.vehicle_type_id
LEFT JOIN zones z ON z.floor_id = f.floor_id
WHERE f.building_id = 'cccc1111-cccc-1111-cccc-111111111111'
ORDER BY f.floor_level;

SELECT
    f.floor_level,
    z.zone_name,
    ps.slot_name,
    ps.slot_status
FROM parking_slots ps
JOIN zones z ON ps.zone_id = z.zone_id
JOIN floors f ON z.floor_id = f.floor_id
WHERE f.building_id = 'cccc1111-cccc-1111-cccc-111111111111'
ORDER BY f.floor_level, z.zone_name, ps.slot_name;
