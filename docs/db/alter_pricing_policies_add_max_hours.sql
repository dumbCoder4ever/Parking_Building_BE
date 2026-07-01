-- Add max_hours column to pricing_policies
-- Safe to re-run: skips if column already exists

USE parking_db;

SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pricing_policies'
      AND COLUMN_NAME = 'max_hours'
);

SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE pricing_policies ADD COLUMN max_hours INT DEFAULT 24 AFTER hourly_rate',
    'SELECT ''Column max_hours already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE pricing_policies
SET max_hours = 24
WHERE max_hours IS NULL;
