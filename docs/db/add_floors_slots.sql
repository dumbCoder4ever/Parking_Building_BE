-- ADDITIONAL SAMPLE FLOORS, ZONES AND PARKING SLOTS
-- ADDITIONAL SAMPLE FLOORS, ZONES AND PARKING SLOTS
-- Creates 4 floors (Floor 1..4) and 30 slots per floor (6 zones a..f × 5 slots each)
-- Only inserts if corresponding rows do not already exist

-- 0) Ensure the floor vehicle types exist.
--    floors.vehicle_type_id is NOT NULL, and each floor in the same building
--    must use a different vehicle_type_id because of the unique constraint.
INSERT INTO vehicle_types (vehicle_type_id, type_name, size_category, description)
SELECT '33333333-3333-3333-3333-333333333331', 'Motorbike', 'SMALL', 'Standard motorbike'
WHERE NOT EXISTS (SELECT 1 FROM vehicle_types WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333331');

INSERT INTO vehicle_types (vehicle_type_id, type_name, size_category, description)
SELECT '33333333-3333-3333-3333-333333333332', 'Car', 'MEDIUM', '4-seat or 7-seat car'
WHERE NOT EXISTS (SELECT 1 FROM vehicle_types WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333332');

INSERT INTO vehicle_types (vehicle_type_id, type_name, size_category, description)
SELECT '33333333-3333-3333-3333-333333333333', 'SUV', 'LARGE', 'Sample SUV type for floor seeding'
WHERE NOT EXISTS (SELECT 1 FROM vehicle_types WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333333');

INSERT INTO vehicle_types (vehicle_type_id, type_name, size_category, description)
SELECT '33333333-3333-3333-3333-333333333334', 'Truck', 'LARGE', 'Sample truck type for floor seeding'
WHERE NOT EXISTS (SELECT 1 FROM vehicle_types WHERE vehicle_type_id = '33333333-3333-3333-3333-333333333334');

-- 1) Insert floors (Floor 1..4) for the Main Parking Building
INSERT INTO floors (building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status)
SELECT b.building_id, '33333333-3333-3333-3333-333333333332', 'Floor 1', 1, 30, 'ACTIVE' FROM buildings b
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM floors f WHERE f.building_id = b.building_id AND f.floor_level = 1);

INSERT INTO floors (building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status)
SELECT b.building_id, '33333333-3333-3333-3333-333333333331', 'Floor 2', 2, 30, 'ACTIVE' FROM buildings b
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM floors f WHERE f.building_id = b.building_id AND f.floor_level = 2);

INSERT INTO floors (building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status)
SELECT b.building_id, '33333333-3333-3333-3333-333333333333', 'Floor 3', 3, 30, 'ACTIVE' FROM buildings b
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM floors f WHERE f.building_id = b.building_id AND f.floor_level = 3);

INSERT INTO floors (building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status)
SELECT b.building_id, '33333333-3333-3333-3333-333333333334', 'Floor 4', 4, 30, 'ACTIVE' FROM buildings b
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM floors f WHERE f.building_id = b.building_id AND f.floor_level = 4);

-- 2) Create 6 zones (a..f) per floor and assign vehicle types
--    Zones a,b,c -> Car ; Zones d,e,f -> Motorbike (each zone max_capacity = 5)
INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status)
SELECT f.floor_id,
       (SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Car' LIMIT 1),
       CONCAT('Zone-a-F', f.floor_level),
       5, 'ACTIVE'
FROM floors f
JOIN buildings b ON f.building_id = b.building_id
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-a-F', f.floor_level));

INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status)
SELECT f.floor_id,
       (SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Car' LIMIT 1),
       CONCAT('Zone-b-F', f.floor_level),
       5, 'ACTIVE'
FROM floors f
JOIN buildings b ON f.building_id = b.building_id
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-b-F', f.floor_level));

INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status)
SELECT f.floor_id,
       (SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Car' LIMIT 1),
       CONCAT('Zone-c-F', f.floor_level),
       5, 'ACTIVE'
FROM floors f
JOIN buildings b ON f.building_id = b.building_id
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-c-F', f.floor_level));

INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status)
SELECT f.floor_id,
       (SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Motorbike' LIMIT 1),
       CONCAT('Zone-d-F', f.floor_level),
       5, 'ACTIVE'
FROM floors f
JOIN buildings b ON f.building_id = b.building_id
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-d-F', f.floor_level));

INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status)
SELECT f.floor_id,
       (SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Motorbike' LIMIT 1),
       CONCAT('Zone-e-F', f.floor_level),
       5, 'ACTIVE'
FROM floors f
JOIN buildings b ON f.building_id = b.building_id
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-e-F', f.floor_level));

INSERT INTO zones (floor_id, vehicle_type_id, zone_name, max_capacity, status)
SELECT f.floor_id,
       (SELECT vehicle_type_id FROM vehicle_types WHERE type_name = 'Motorbike' LIMIT 1),
       CONCAT('Zone-f-F', f.floor_level),
       5, 'ACTIVE'
FROM floors f
JOIN buildings b ON f.building_id = b.building_id
WHERE b.building_name = 'Main Parking Building'
  AND NOT EXISTS (SELECT 1 FROM zones z WHERE z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-f-F', f.floor_level));

-- 3) Insert parking slots: 5 slots per zone (letters a..f)
-- Uses a derived numbers table (1..5)
-- Slot naming convention: <ZONELETTER>-F<floor>-NN (e.g., A-F1-01)
INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('A-F', f.floor_level, '-', LPAD(n.n,2,'0')), 'AVAILABLE'
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-a-F', f.floor_level)
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('A-F', f.floor_level, '-', LPAD(n.n,2,'0'))
WHERE ps.slot_id IS NULL;

INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('B-F', f.floor_level, '-', LPAD(n.n,2,'0')), 'AVAILABLE'
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-b-F', f.floor_level)
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('B-F', f.floor_level, '-', LPAD(n.n,2,'0'))
WHERE ps.slot_id IS NULL;

INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('C-F', f.floor_level, '-', LPAD(n.n,2,'0')), 'AVAILABLE'
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-c-F', f.floor_level)
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('C-F', f.floor_level, '-', LPAD(n.n,2,'0'))
WHERE ps.slot_id IS NULL;

INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('D-F', f.floor_level, '-', LPAD(n.n,2,'0')), 'AVAILABLE'
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-d-F', f.floor_level)
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('D-F', f.floor_level, '-', LPAD(n.n,2,'0'))
WHERE ps.slot_id IS NULL;

INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('E-F', f.floor_level, '-', LPAD(n.n,2,'0')), 'AVAILABLE'
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-e-F', f.floor_level)
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('E-F', f.floor_level, '-', LPAD(n.n,2,'0'))
WHERE ps.slot_id IS NULL;

INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('F-F', f.floor_level, '-', LPAD(n.n,2,'0')), 'AVAILABLE'
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id AND z.zone_name = CONCAT('Zone-f-F', f.floor_level)
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('F-F', f.floor_level, '-', LPAD(n.n,2,'0'))
WHERE ps.slot_id IS NULL;

-- 5) Update floor current_occupancy and building total_floors if needed
-- Update floors current_occupancy using a safe multi-table update
UPDATE floors f
JOIN (
  SELECT f.floor_id AS fid, COUNT(ps.slot_id) AS cnt
  FROM floors f
  LEFT JOIN zones z ON z.floor_id = f.floor_id
  LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id
  GROUP BY f.floor_id
) t ON t.fid = f.floor_id
SET f.current_occupancy = t.cnt
WHERE f.floor_id IS NOT NULL;

UPDATE buildings b
SET b.total_floors = GREATEST(b.total_floors, 4)
WHERE b.building_name = 'Main Parking Building';

-- =========================================================
-- FIXES: normalize slot_status and deduplicate vehicle_types
-- Idempotent: safe to run multiple times
-- 1) Normalize invalid slot_status values to 'AVAILABLE'
UPDATE parking_slots
SET slot_status = 'AVAILABLE'
WHERE slot_status IS NULL
   OR slot_status NOT IN ('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE');

-- 2) Ensure parking_slots.slot_status has a safe DEFAULT and NOT NULL
--    (Some MySQL servers will fail this ALTER if invalid values remain.)
ALTER TABLE parking_slots
  MODIFY COLUMN slot_status ENUM('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE';

-- 3) Deduplicate vehicle_types by type_name: choose canonical id = MIN(vehicle_type_id)
--    Update referencing zones and vehicles to canonical id, then delete duplicates
CREATE TEMPORARY TABLE IF NOT EXISTS vt_canonical AS
SELECT type_name, MIN(vehicle_type_id) AS canonical_vehicle_type_id
FROM vehicle_types
GROUP BY type_name
HAVING COUNT(*) > 1;

-- Update zones to canonical vehicle_type_id
UPDATE zones z
JOIN vehicle_types vt ON z.vehicle_type_id = vt.vehicle_type_id
JOIN vt_canonical c ON vt.type_name = c.type_name
SET z.vehicle_type_id = c.canonical_vehicle_type_id
WHERE z.vehicle_type_id <> c.canonical_vehicle_type_id;

-- Update vehicles to canonical vehicle_type_id
UPDATE vehicles v
JOIN vehicle_types vt ON v.vehicle_type_id = vt.vehicle_type_id
JOIN vt_canonical c ON vt.type_name = c.type_name
SET v.vehicle_type_id = c.canonical_vehicle_type_id
WHERE v.vehicle_type_id <> c.canonical_vehicle_type_id;

-- Delete duplicate vehicle_type rows (keep canonical)
DELETE vt FROM vehicle_types vt
JOIN vt_canonical c ON vt.type_name = c.type_name
WHERE vt.vehicle_type_id <> c.canonical_vehicle_type_id;

DROP TEMPORARY TABLE IF EXISTS vt_canonical;

-- 4) Recalculate floor occupancy safely
UPDATE floors f
JOIN (
    SELECT f.floor_id AS fid, COUNT(ps.slot_id) AS cnt
    FROM floors f
    LEFT JOIN zones z ON z.floor_id = f.floor_id
    LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id
    GROUP BY f.floor_id
) t ON t.fid = f.floor_id
SET f.current_occupancy = t.cnt
WHERE f.floor_id IS NOT NULL;
