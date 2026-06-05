-- Add building_staff table to existing parking_db
-- Copy & run this block directly in MySQL Workbench / phpMyAdmin

USE parking_db;

CREATE TABLE IF NOT EXISTS building_staff (
    assignment_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_building_staff_building
        FOREIGN KEY (building_id)
            REFERENCES buildings(building_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_building_staff_user
        FOREIGN KEY (user_id)
            REFERENCES users(user_id)
            ON DELETE CASCADE,

    CONSTRAINT uq_building_staff
        UNIQUE (building_id, user_id)
);
