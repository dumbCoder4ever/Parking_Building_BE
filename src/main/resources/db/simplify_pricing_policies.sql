-- Migration: Simplify pricing_policies table
-- Remove tiered pricing columns, add max_hours (MySQL compatible)

-- 1. Check if column exists, if not add it
SET @column_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                      WHERE TABLE_SCHEMA = DATABASE() 
                      AND TABLE_NAME = 'pricing_policies' 
                      AND COLUMN_NAME = 'max_hours');

SET @sql = IF(@column_exists = 0, 
              'ALTER TABLE pricing_policies ADD COLUMN max_hours INT DEFAULT 24',
              'SELECT ''Column max_hours already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Update existing records to set max_hours = 24
UPDATE pricing_policies SET max_hours = 24 WHERE max_hours IS NULL;
