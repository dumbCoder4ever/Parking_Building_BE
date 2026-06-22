-- Add max_hours column to reservations table
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS max_hours INT DEFAULT 24;

-- Update existing reservations with default max_hours
UPDATE reservations SET max_hours = 24 WHERE max_hours IS NULL;
