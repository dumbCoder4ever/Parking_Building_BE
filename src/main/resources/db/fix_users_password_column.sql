-- Fix users table for Spring Boot (run each block in MySQL Workbench; ignore benign errors noted below)
USE parking_db;

-- Step 1: Remove legacy "password" column (entity uses password_hash only)
-- If you get Error 1091 "Can't DROP 'password'; check that column exists" -> skip, column already removed
ALTER TABLE users DROP COLUMN password;

-- Step 2: Align role ENUM with parking_db.sql (self-register uses ROLE_DRIVER only)
ALTER TABLE users MODIFY COLUMN role ENUM(
    'ROLE_ADMIN',
    'ROLE_MANAGER',
    'ROLE_STAFF',
    'ROLE_DRIVER'
) NOT NULL;

-- Step 3: Soft-delete columns for User entity (run only if DESCRIBE users does not list them)
-- If Error 1060 "Duplicate column name" -> columns already exist, skip
ALTER TABLE users ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN deleted_at DATETIME NULL;
