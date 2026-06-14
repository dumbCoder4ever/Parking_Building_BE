-- Script để seed slots cho tất cả zones hiện có
-- Chạy script này 1 lần để tạo slots

-- Tạo slots cho mỗi zone (nếu zone chưa có slots)
-- Mỗi zone sẽ có 10 slots (có thể điều chỉnh số lượng)

-- Disable foreign key checks مؤقتاً
SET FOREIGN_KEY_CHECKS = 0;

-- Tạo slots cho Zone A (Floor 1 - Car)
INSERT IGNORE INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('S', n.num), 'AVAILABLE'
FROM zones z
CROSS JOIN (
    SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
) n
WHERE z.zone_name = 'Zone A'
AND NOT EXISTS (
    SELECT 1 FROM parking_slots ps WHERE ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('S', n.num)
);

-- Tạo slots cho Zone B (Floor 1 - Car)
INSERT IGNORE INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('S', n.num), 'AVAILABLE'
FROM zones z
CROSS JOIN (
    SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
) n
WHERE z.zone_name = 'Zone B'
AND NOT EXISTS (
    SELECT 1 FROM parking_slots ps WHERE ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('S', n.num)
);

-- Tạo slots cho tất cả zones còn lại (mỗi zone 10 slots)
INSERT IGNORE INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('S', n.num), 'AVAILABLE'
FROM zones z
CROSS JOIN (
    SELECT 1 AS num UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
    UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
) n
WHERE NOT EXISTS (
    SELECT 1 FROM parking_slots ps WHERE ps.zone_id = z.zone_id AND ps.slot_name = CONCAT('S', n.num)
);

-- Enable foreign key checks lại
SET FOREIGN_KEY_CHECKS = 1;

-- Hiển thị kết quả
SELECT 
    f.floor_name,
    z.zone_name,
    COUNT(ps.slot_id) AS total_slots,
    SUM(CASE WHEN ps.slot_status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available
FROM floors f
JOIN zones z ON z.floor_id = f.floor_id
LEFT JOIN parking_slots ps ON ps.zone_id = z.zone_id
GROUP BY f.floor_name, z.zone_name
ORDER BY f.floor_level, z.zone_name;
