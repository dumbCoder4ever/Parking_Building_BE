-- Add checkin_type column to parking_sessions table
-- This column identifies the type of checkin: RESERVATION, DRIVER_WALK_IN, or GUEST

ALTER TABLE parking_sessions ADD COLUMN checkin_type VARCHAR(30) DEFAULT NULL;

-- Update existing sessions based on their current state
-- Sessions with reservation are RESERVATION
UPDATE parking_sessions
SET checkin_type = 'RESERVATION'
WHERE reservation_id IS NOT NULL AND checkin_type IS NULL;

-- Sessions with user but no reservation are DRIVER_WALK_IN
UPDATE parking_sessions
SET checkin_type = 'DRIVER_WALK_IN'
WHERE user_id IS NOT NULL AND reservation_id IS NULL AND checkin_type IS NULL;

-- Sessions without user are GUEST
UPDATE parking_sessions
SET checkin_type = 'GUEST'
WHERE user_id IS NULL AND checkin_type IS NULL;

-- Add index for faster queries by checkin_type (ignore error if already exists)
CREATE INDEX idx_parking_sessions_checkin_type ON parking_sessions(checkin_type);
