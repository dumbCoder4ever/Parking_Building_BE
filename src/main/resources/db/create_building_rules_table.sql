-- Create building_rules table (MySQL / Railway Query Editor compatible)
-- Idempotent: safe to run multiple times

CREATE TABLE IF NOT EXISTS building_rules (
    rule_id VARCHAR(36) PRIMARY KEY,
    building_id VARCHAR(36) NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    rule_value VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_building_rules_building (building_id),
    INDEX idx_building_rules_status (status),
    INDEX idx_building_rules_code (rule_code),
    CONSTRAINT fk_building_rules_building
        FOREIGN KEY (building_id) REFERENCES buildings(building_id)
);
