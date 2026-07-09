-- =========================================================
-- PARKING MANAGEMENT SYSTEM - RAILWAY FULL MIGRATION
-- Run this file in Railway MySQL Console (Query tab)
-- Database: railway (auto-created by Railway)
-- =========================================================

-- =========================================================
-- 1. DROP ALL EXISTING TABLES (clean slate)
-- =========================================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS checkout_requests;
DROP TABLE IF EXISTS incidents;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS parking_sessions;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS parking_slots;
DROP TABLE IF EXISTS zones;
DROP TABLE IF EXISTS floors;
DROP TABLE IF EXISTS pricing_policies;
DROP TABLE IF EXISTS vehicle_types;
DROP TABLE IF EXISTS building_staff;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS buildings;
SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 2. CREATE USERS TABLE
-- =========================================================
CREATE TABLE users (
    user_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    phone_number VARCHAR(20),
    email VARCHAR(100),
    avatar_url VARCHAR(255),
    role ENUM('ROLE_ADMIN','ROLE_MANAGER','ROLE_STAFF','ROLE_DRIVER') NOT NULL,
    status ENUM('ACTIVE','INACTIVE','BANNED') DEFAULT 'ACTIVE',
    is_active BOOLEAN DEFAULT TRUE,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME,
    last_login DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 3. CREATE BUILDINGS TABLE
-- =========================================================
CREATE TABLE buildings (
    building_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    total_floors INT NOT NULL,
    operating_start_time TIME,
    operating_end_time TIME,
    contact_number VARCHAR(20),
    status ENUM('ACTIVE','INACTIVE','MAINTENANCE') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 4. CREATE BUILDING STAFF TABLE
-- =========================================================
CREATE TABLE building_staff (
    assignment_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_building_staff_building FOREIGN KEY (building_id) REFERENCES buildings(building_id) ON DELETE CASCADE,
    CONSTRAINT fk_building_staff_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_building_staff UNIQUE (building_id, user_id)
);

-- =========================================================
-- 5. CREATE VEHICLE TYPES TABLE
-- =========================================================
CREATE TABLE vehicle_types (
    vehicle_type_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    type_name VARCHAR(50) NOT NULL,
    size_category VARCHAR(30),
    description VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 6. CREATE FLOORS TABLE
-- =========================================================
CREATE TABLE floors (
    floor_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_id CHAR(36) NOT NULL,
    vehicle_type_id CHAR(36) NOT NULL,
    floor_name VARCHAR(50) NOT NULL,
    floor_level INT NOT NULL,
    max_capacity INT DEFAULT 0,
    current_occupancy INT DEFAULT 0,
    status ENUM('ACTIVE','INACTIVE','MAINTENANCE') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_floors_building FOREIGN KEY (building_id) REFERENCES buildings(building_id) ON DELETE CASCADE,
    CONSTRAINT fk_floors_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(vehicle_type_id),
    CONSTRAINT uq_building_vehicle_type UNIQUE (building_id, vehicle_type_id)
);

-- =========================================================
-- 7. CREATE ZONES TABLE
-- =========================================================
CREATE TABLE zones (
    zone_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    floor_id CHAR(36) NOT NULL,
    zone_name VARCHAR(50) NOT NULL,
    max_capacity INT DEFAULT 0,
    current_occupancy INT DEFAULT 0,
    status ENUM('ACTIVE','INACTIVE','FULL','MAINTENANCE') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_zones_floor FOREIGN KEY (floor_id) REFERENCES floors(floor_id) ON DELETE CASCADE
);

-- =========================================================
-- 8. CREATE PARKING SLOTS TABLE
-- =========================================================
CREATE TABLE parking_slots (
    slot_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    zone_id CHAR(36) NOT NULL,
    slot_name VARCHAR(50) NOT NULL,
    slot_status ENUM('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE') DEFAULT 'AVAILABLE',
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_slot_name UNIQUE (zone_id, slot_name),
    CONSTRAINT fk_slots_zone FOREIGN KEY (zone_id) REFERENCES zones(zone_id) ON DELETE CASCADE
);

-- =========================================================
-- 9. CREATE VEHICLES TABLE
-- =========================================================
CREATE TABLE vehicles (
    vehicle_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id CHAR(36) NOT NULL,
    vehicle_type_id CHAR(36) NOT NULL,
    plate_number VARCHAR(20) NOT NULL UNIQUE,
    vehicle_color VARCHAR(30),
    brand VARCHAR(50),
    model VARCHAR(50),
    status ENUM('ACTIVE','INACTIVE','BLOCKED') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicles_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(vehicle_type_id)
);

-- =========================================================
-- 10. CREATE RESERVATIONS TABLE
-- =========================================================
CREATE TABLE reservations (
    reservation_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id CHAR(36) NOT NULL,
    vehicle_id CHAR(36) NOT NULL,
    slot_id CHAR(36) NOT NULL,
    reservation_code VARCHAR(50) NOT NULL UNIQUE,
    reservation_start DATETIME NOT NULL,
    reservation_end DATETIME NOT NULL,
    grace_period_minutes INT DEFAULT 15,
    estimated_fee DECIMAL(10,2) DEFAULT 0,
    reservation_status ENUM('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED','COMPLETED') DEFAULT 'PENDING',
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_reservations_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_reservations_slot FOREIGN KEY (slot_id) REFERENCES parking_slots(slot_id)
);

-- =========================================================
-- 11. CREATE TICKETS TABLE
-- =========================================================
CREATE TABLE tickets (
    ticket_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    reservation_id CHAR(36) NOT NULL UNIQUE,
    ticket_code VARCHAR(50) NOT NULL UNIQUE,
    is_used BOOLEAN DEFAULT FALSE,
    is_lost BOOLEAN DEFAULT FALSE,
    status ENUM('ACTIVE','USED','EXPIRED','LOST') DEFAULT 'ACTIVE',
    issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id) ON DELETE CASCADE
);

-- =========================================================
-- 12. CREATE PARKING SESSIONS TABLE
-- =========================================================
CREATE TABLE parking_sessions (
    session_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    vehicle_id CHAR(36) NOT NULL,
    slot_id CHAR(36) NOT NULL,
    ticket_id CHAR(36),
    reservation_id CHAR(36),
    checkin_time DATETIME NOT NULL,
    checkout_time DATETIME,
    estimated_fee DECIMAL(10,2) DEFAULT 0,
    total_fee DECIMAL(10,2) DEFAULT 0,
    parking_duration INT DEFAULT 0,
    payment_status ENUM('UNPAID','PAID','FAILED') DEFAULT 'UNPAID',
    session_status ENUM('ACTIVE','PENDING_PAYMENT','PENDING_EXIT','COMPLETED','CANCELLED') DEFAULT 'ACTIVE',
    note VARCHAR(255),
    created_by CHAR(36),
    updated_by CHAR(36),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_sessions_slot FOREIGN KEY (slot_id) REFERENCES parking_slots(slot_id),
    CONSTRAINT fk_sessions_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id),
    CONSTRAINT fk_sessions_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT fk_sessions_updated_by FOREIGN KEY (updated_by) REFERENCES users(user_id)
);

-- =========================================================
-- 13. CREATE PAYMENTS TABLE
-- =========================================================
CREATE TABLE payments (
    payment_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    session_id CHAR(36) NOT NULL,
    payment_method ENUM('CASH','BANKING','MOMO','VNPAY','PAYOS') NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    payment_status ENUM('PENDING','PAID','CONFIRMED','SUCCESS','FAILED') DEFAULT 'PENDING',
    transaction_code VARCHAR(100),
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_session FOREIGN KEY (session_id) REFERENCES parking_sessions(session_id) ON DELETE CASCADE
);

-- =========================================================
-- 14. CREATE PRICING POLICIES TABLE
-- =========================================================
CREATE TABLE pricing_policies (
    policy_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    vehicle_type_id CHAR(36) NOT NULL,
    policy_name VARCHAR(100) NOT NULL,
    pricing_type ENUM('HOURLY','DAILY','OVERNIGHT','TIERED') NOT NULL,
    base_price DECIMAL(10,2) DEFAULT 0,
    hourly_rate DECIMAL(10,2) DEFAULT 0,
    overnight_fee DECIMAL(10,2) DEFAULT 0,
    lost_ticket_fee DECIMAL(10,2) DEFAULT 0,
    peak_hour_multiplier DECIMAL(5,2) DEFAULT 1,
    max_daily_fee DECIMAL(10,2) DEFAULT 0,
    tier1_hours INT DEFAULT 0,
    tier1_price DECIMAL(10,2) DEFAULT 0,
    tier2_hours INT DEFAULT 0,
    tier2_price DECIMAL(10,2) DEFAULT 0,
    tier3_hours INT DEFAULT 0,
    tier3_price DECIMAL(10,2) DEFAULT 0,
    tier4_hours INT DEFAULT 0,
    tier4_price DECIMAL(10,2) DEFAULT 0,
    per_day_price DECIMAL(10,2) DEFAULT 0,
    max_hours INT DEFAULT 24,
    effective_from DATETIME,
    effective_to DATETIME,
    status ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_policy_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(vehicle_type_id)
);

-- =========================================================
-- 15. CREATE INCIDENTS TABLE
-- =========================================================
CREATE TABLE incidents (
    incident_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    session_id CHAR(36) NOT NULL,
    incident_type ENUM('LOST_TICKET','WRONG_VEHICLE','OVERTIME','UNPAID','OTHER') NOT NULL,
    description VARCHAR(500),
    status ENUM('OPEN','PROCESSING','RESOLVED') DEFAULT 'OPEN',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incidents_session FOREIGN KEY (session_id) REFERENCES parking_sessions(session_id) ON DELETE CASCADE
);

-- =========================================================
-- 16. INSERT SEED DATA: USERS
-- Password for all users: 123 (BCrypt hashed)
-- =========================================================
INSERT INTO users (user_id, username, password_hash, full_name, email, role, status) VALUES
('11111111-1111-1111-1111-111111111111', 'admin', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'System Administrator', 'admin@parking.com', 'ROLE_ADMIN', 'ACTIVE'),
('22222222-2222-2222-2222-222222222222', 'manager1', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Parking Manager', 'manager@parking.com', 'ROLE_MANAGER', 'ACTIVE'),
('33333333-3333-3333-3333-333333333333', 'staff1', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Parking Staff', 'staff@parking.com', 'ROLE_STAFF', 'ACTIVE'),
('44444444-4444-4444-4444-444444444444', 'driver1', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Driver User', 'driver@parking.com', 'ROLE_DRIVER', 'ACTIVE');

-- =========================================================
-- 17. INSERT SEED DATA: VEHICLE TYPES
-- =========================================================
INSERT INTO vehicle_types (vehicle_type_id, type_name, size_category, description) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Motorbike', 'SMALL', 'Standard motorbike'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Car', 'MEDIUM', '4-seat or 7-seat car'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'SUV', 'LARGE', 'SUV vehicle'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Truck', 'LARGE', 'Truck vehicle');

-- =========================================================
-- 18. INSERT SEED DATA: BUILDING
-- =========================================================
INSERT INTO buildings (building_id, building_name, address, total_floors, operating_start_time, operating_end_time, contact_number, status) VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Main Parking Building', 'District 1, Ho Chi Minh City', 4, '06:00:00', '23:00:00', '0909000000', 'ACTIVE');

-- =========================================================
-- 19. INSERT SEED DATA: BUILDING STAFF
-- =========================================================
INSERT INTO building_staff (assignment_id, building_id, user_id) VALUES
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333');

-- =========================================================
-- 20. INSERT SEED DATA: FLOORS
-- =========================================================
INSERT INTO floors (floor_id, building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status) VALUES
('f1111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Floor 1 - Car', 1, 30, 'ACTIVE'),
('f2222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Floor 2 - Motorbike', 2, 30, 'ACTIVE'),
('f3333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'Floor 3 - SUV', 3, 20, 'ACTIVE'),
('f4444444-4444-4444-4444-444444444444', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Floor 4 - Truck', 4, 20, 'ACTIVE');

-- =========================================================
-- 21. INSERT SEED DATA: ZONES
-- =========================================================
-- Floor 1 - Car (6 zones)
INSERT INTO zones (zone_id, floor_id, zone_name, max_capacity, status) VALUES
('z1111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'Zone-A-F1', 5, 'ACTIVE'),
('z1111111-1111-1111-1111-111111111112', 'f1111111-1111-1111-1111-111111111111', 'Zone-B-F1', 5, 'ACTIVE'),
('z1111111-1111-1111-1111-111111111113', 'f1111111-1111-1111-1111-111111111111', 'Zone-C-F1', 5, 'ACTIVE');

-- Floor 2 - Motorbike (6 zones)
INSERT INTO zones (zone_id, floor_id, zone_name, max_capacity, status) VALUES
('z2222222-2222-2222-2222-222222222221', 'f2222222-2222-2222-2222-222222222222', 'Zone-A-F2', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 'Zone-B-F2', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222223', 'f2222222-2222-2222-2222-222222222222', 'Zone-C-F2', 5, 'ACTIVE');

-- =========================================================
-- 22. INSERT SEED DATA: PARKING SLOTS
-- =========================================================
-- Zone-A-F1: 5 slots
INSERT INTO parking_slots (zone_id, slot_name, slot_status) VALUES
('z1111111-1111-1111-1111-111111111111', 'A-F1-01', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111111', 'A-F1-02', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111111', 'A-F1-03', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111111', 'A-F1-04', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111111', 'A-F1-05', 'AVAILABLE');

-- Zone-B-F1: 5 slots
INSERT INTO parking_slots (zone_id, slot_name, slot_status) VALUES
('z1111111-1111-1111-1111-111111111112', 'B-F1-01', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111112', 'B-F1-02', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111112', 'B-F1-03', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111112', 'B-F1-04', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111112', 'B-F1-05', 'AVAILABLE');

-- Zone-C-F1: 5 slots
INSERT INTO parking_slots (zone_id, slot_name, slot_status) VALUES
('z1111111-1111-1111-1111-111111111113', 'C-F1-01', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111113', 'C-F1-02', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111113', 'C-F1-03', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111113', 'C-F1-04', 'AVAILABLE'),
('z1111111-1111-1111-1111-111111111113', 'C-F1-05', 'AVAILABLE');

-- Zone-A-F2: 5 slots
INSERT INTO parking_slots (zone_id, slot_name, slot_status) VALUES
('z2222222-2222-2222-2222-222222222221', 'A-F2-01', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222221', 'A-F2-02', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222221', 'A-F2-03', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222221', 'A-F2-04', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222221', 'A-F2-05', 'AVAILABLE');

-- Zone-B-F2: 5 slots
INSERT INTO parking_slots (zone_id, slot_name, slot_status) VALUES
('z2222222-2222-2222-2222-222222222222', 'B-F2-01', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222222', 'B-F2-02', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222222', 'B-F2-03', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222222', 'B-F2-04', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222222', 'B-F2-05', 'AVAILABLE');

-- Zone-C-F2: 5 slots
INSERT INTO parking_slots (zone_id, slot_name, slot_status) VALUES
('z2222222-2222-2222-2222-222222222223', 'C-F2-01', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222223', 'C-F2-02', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222223', 'C-F2-03', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222223', 'C-F2-04', 'AVAILABLE'),
('z2222222-2222-2222-2222-222222222223', 'C-F2-05', 'AVAILABLE');

-- =========================================================
-- 23. INSERT SEED DATA: PRICING POLICIES
-- =========================================================
INSERT INTO pricing_policies (vehicle_type_id, policy_name, pricing_type, tier1_hours, tier1_price, tier2_hours, tier2_price, tier3_hours, tier3_price, tier4_hours, tier4_price, per_day_price, max_hours, status) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Motorbike Standard', 'TIERED', 2, 5000.00, 6, 10000.00, 12, 15000.00, 24, 20000.00, 20000.00, 24, 'ACTIVE'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Car Standard', 'TIERED', 2, 20000.00, 6, 40000.00, 12, 60000.00, 24, 100000.00, 100000.00, 24, 'ACTIVE'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'SUV Standard', 'TIERED', 2, 25000.00, 6, 50000.00, 12, 75000.00, 24, 120000.00, 120000.00, 24, 'ACTIVE'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Truck Standard', 'TIERED', 2, 30000.00, 6, 60000.00, 12, 90000.00, 24, 150000.00, 150000.00, 24, 'ACTIVE');

-- =========================================================
-- 24. INSERT SEED DATA: SAMPLE VEHICLE FOR DRIVER
-- =========================================================
INSERT INTO vehicles (vehicle_id, user_id, vehicle_type_id, plate_number, vehicle_color, brand, model, status) VALUES
('vv111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444444', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '51A-12345', 'White', 'Toyota', 'Vios', 'ACTIVE');

-- =========================================================
-- VERIFICATION
-- =========================================================
SELECT '=== Migration completed successfully! ===' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = DATABASE();
SELECT 'Users:' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'Buildings:', COUNT(*) FROM buildings
UNION ALL SELECT 'Floors:', COUNT(*) FROM floors
UNION ALL SELECT 'Zones:', COUNT(*) FROM zones
UNION ALL SELECT 'Parking Slots:', COUNT(*) FROM parking_slots
UNION ALL SELECT 'Vehicle Types:', COUNT(*) FROM vehicle_types
UNION ALL SELECT 'Vehicles:', COUNT(*) FROM vehicles
UNION ALL SELECT 'Pricing Policies:', COUNT(*) FROM pricing_policies;
