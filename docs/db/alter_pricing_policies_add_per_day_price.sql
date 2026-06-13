-- =========================================================
-- ALTER TABLE: add missing columns to pricing_policies
-- Run this script against parking_db
-- =========================================================

USE parking_db;

-- Add per_day_price column if it doesn't exist
ALTER TABLE pricing_policies
ADD COLUMN IF NOT EXISTS per_day_price DECIMAL(10,2) DEFAULT 0 AFTER tier4_price;
