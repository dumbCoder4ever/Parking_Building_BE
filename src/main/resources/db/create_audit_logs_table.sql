-- Create audit_logs table
-- Note: Uses PROCEDURE to safely add columns/tables only if they don't exist

DROP PROCEDURE IF EXISTS create_audit_logs_table;

DELIMITER //
CREATE PROCEDURE create_audit_logs_table()
BEGIN
    DECLARE table_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO table_exists
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'audit_logs';
    
    IF table_exists = 0 THEN
        CREATE TABLE audit_logs (
            log_id VARCHAR(36) PRIMARY KEY,
            action_type VARCHAR(50) NOT NULL,
            entity_type VARCHAR(50),
            entity_id VARCHAR(36),
            user_id VARCHAR(36),
            username VARCHAR(100),
            description VARCHAR(500),
            old_value VARCHAR(500),
            new_value VARCHAR(500),
            ip_address VARCHAR(45),
            user_agent VARCHAR(255),
            building_id VARCHAR(36),
            metadata TEXT,
            created_at DATETIME NOT NULL,
            INDEX idx_action_type (action_type),
            INDEX idx_entity_type_id (entity_type, entity_id),
            INDEX idx_user_id (user_id),
            INDEX idx_building_id (building_id),
            INDEX idx_created_at (created_at)
        );
    END IF;
END //
DELIMITER ;

CALL create_audit_logs_table();
DROP PROCEDURE IF EXISTS create_audit_logs_table;
