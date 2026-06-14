-- Fix slot_status enum - add PENDING_EXIT
-- =========================================================

-- Backup current data
SELECT 'Backup current slot statuses' as action;
SELECT slot_id, slot_name, slot_status FROM parking_slots WHERE slot_status = 'PENDING_EXIT';

-- Drop existing enum if no PENDING_EXIT slots exist
-- This is safe because code uses PENDING_EXIT for pending-exit state

ALTER TABLE parking_slots
MODIFY COLUMN slot_status ENUM(
    'AVAILABLE',
    'RESERVED',
    'OCCUPIED',
    'MAINTENANCE',
    'PENDING_EXIT'
) DEFAULT 'AVAILABLE';

-- Verify the change
SELECT 'After ALTER TABLE' as action;
SHOW COLUMNS FROM parking_slots LIKE 'slot_status';

-- Update any existing PENDING_EXIT to AVAILABLE (if exists from previous run)
UPDATE parking_slots SET slot_status = 'AVAILABLE' WHERE slot_status = 'PENDING_EXIT';
