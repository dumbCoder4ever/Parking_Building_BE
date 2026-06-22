-- Add new columns to parking_sessions table for image fields and audit fields
-- Note: If column already exists, use the DROP COLUMN version below or ignore errors

-- Add columns if they don't exist (MySQL compatible approach using PROCEDURE)
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN table_name VARCHAR(255),
    IN column_name VARCHAR(255),
    IN column_definition VARCHAR(500)
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND COLUMN_NAME = column_name;
    
    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', table_name, ' ADD COLUMN ', column_name, ' ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- Add columns to parking_sessions
CALL add_column_if_not_exists('parking_sessions', 'checkin_plate_image', 'VARCHAR(500)');
CALL add_column_if_not_exists('parking_sessions', 'checkin_vehicle_image', 'VARCHAR(500)');
CALL add_column_if_not_exists('parking_sessions', 'checkout_plate_image', 'VARCHAR(500)');
CALL add_column_if_not_exists('parking_sessions', 'checkout_vehicle_image', 'VARCHAR(500)');
CALL add_column_if_not_exists('parking_sessions', 'checkin_staff_id', 'VARCHAR(36)');
CALL add_column_if_not_exists('parking_sessions', 'checkout_staff_id', 'VARCHAR(36)');
CALL add_column_if_not_exists('parking_sessions', 'pricing_policy_id', 'VARCHAR(36)');
CALL add_column_if_not_exists('parking_sessions', 'pricing_version', 'INT DEFAULT 1');

-- Add version column to pricing_policies
CALL add_column_if_not_exists('pricing_policies', 'version', 'INT DEFAULT 1');

-- Drop procedure after use
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- Add foreign keys (check if exists first)
-- Note: Foreign keys in MySQL need to be dropped and re-added if they exist
-- For simplicity, we'll add them - they'll fail gracefully if already exist

-- Add foreign key for checkin_staff_id
-- ALTER TABLE parking_sessions ADD CONSTRAINT fk_checkin_staff FOREIGN KEY (checkin_staff_id) REFERENCES users(user_id);

-- Add foreign key for checkout_staff_id
-- ALTER TABLE parking_sessions ADD CONSTRAINT fk_checkout_staff FOREIGN KEY (checkout_staff_id) REFERENCES users(user_id);

-- Add foreign key for pricing_policy_id
-- ALTER TABLE parking_sessions ADD CONSTRAINT fk_pricing_policy FOREIGN KEY (pricing_policy_id) REFERENCES pricing_policies(policy_id);

-- Update session_status values for existing records
-- First ensure column has enough length for 'CHECKED_IN' (10 chars)
ALTER TABLE parking_sessions MODIFY COLUMN session_status VARCHAR(20);
UPDATE parking_sessions SET session_status = 'CHECKED_IN' WHERE session_status = 'ACTIVE';

-- Update reservation_status values for existing records
-- First ensure column has enough length for 'PENDING_PAYMENT' (15 chars)
ALTER TABLE reservations MODIFY COLUMN reservation_status VARCHAR(20);
UPDATE reservations SET reservation_status = 'PENDING_PAYMENT' WHERE reservation_status = 'PENDING';
UPDATE reservations SET reservation_status = 'PAID' WHERE reservation_status = 'APPROVED';
