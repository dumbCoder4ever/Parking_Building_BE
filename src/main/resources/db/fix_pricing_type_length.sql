-- =========================================================
-- FIX: pricing_type column too short
-- Error: Data truncated for column 'pricing_type' at row 1
-- Cause: DB thực tế có column nhỏ hơn 40 (do schema cũ)
-- Fix: MODIFY column về VARCHAR(40) để khớp với entity
-- Safe to re-run: SELECT trước để xem current type
-- =========================================================

USE parking_db;

-- Check current type trước khi ALTER (log only)
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'parking_db'
  AND TABLE_NAME = 'pricing_policies'
  AND COLUMN_NAME = 'pricing_type';

-- MODIFY về VARCHAR(40) - an toàn vì:
--   - Tăng length luôn safe với VARCHAR (không mất data)
--   - Không ảnh hưởng data hiện tại (STANDARD, TIERED, ... đều < 40)
ALTER TABLE pricing_policies
MODIFY COLUMN pricing_type VARCHAR(40) DEFAULT NULL;

-- Verify sau khi ALTER
SELECT COLUMN_NAME, COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'parking_db'
  AND TABLE_NAME = 'pricing_policies'
  AND COLUMN_NAME = 'pricing_type';