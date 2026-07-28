-- =========================================================
-- PARKING MANAGEMENT SYSTEM - INIT SQL
-- File khởi tạo DB lần đầu (chỉ CREATE, không DROP)
-- Cập nhật: 2026-07-28
-- =========================================================

-- =========================================================
-- 1. USERS
-- Quản lý tất cả user: Admin, Manager, Staff, Driver
-- =========================================================
CREATE TABLE IF NOT EXISTS users (
    user_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    phone_number VARCHAR(20),
    email VARCHAR(100) UNIQUE,
    avatar_url VARCHAR(255),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login DATETIME,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 2. BUILDINGS
-- Tòa nhà đỗ xe
-- =========================================================
CREATE TABLE IF NOT EXISTS buildings (
    building_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    total_floors INT,
    operating_start_time TIME,
    operating_end_time TIME,
    contact_number VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 3. VEHICLE_TYPES
-- Loại xe (Motorbike, Car, SUV, Truck)
-- =========================================================
CREATE TABLE IF NOT EXISTS vehicle_types (
    vehicle_type_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    type_name VARCHAR(50) NOT NULL,
    size_category VARCHAR(30),
    description VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 4. BUILDING_STAFF
-- Phân công staff cho từng building (Many-to-Many)
-- =========================================================
CREATE TABLE IF NOT EXISTS building_staff (
    assignment_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_building_staff_building FOREIGN KEY (building_id) REFERENCES buildings(building_id) ON DELETE CASCADE,
    CONSTRAINT fk_building_staff_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_building_staff UNIQUE (building_id, user_id)
);

-- =========================================================
-- 5. FLOORS
-- Tầng trong tòa nhà
-- =========================================================
CREATE TABLE IF NOT EXISTS floors (
    floor_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_id CHAR(36) NOT NULL,
    vehicle_type_id CHAR(36) NOT NULL,
    floor_name VARCHAR(50) NOT NULL,
    floor_level INT NOT NULL,
    max_capacity INT DEFAULT 0,
    current_occupancy INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_floors_building FOREIGN KEY (building_id) REFERENCES buildings(building_id) ON DELETE CASCADE,
    CONSTRAINT fk_floors_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(vehicle_type_id)
);

-- =========================================================
-- 6. ZONES
-- Khu vực trong tầng
-- =========================================================
CREATE TABLE IF NOT EXISTS zones (
    zone_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    floor_id CHAR(36) NOT NULL,
    zone_name VARCHAR(50) NOT NULL,
    max_capacity INT DEFAULT 0,
    current_occupancy INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_zones_floor FOREIGN KEY (floor_id) REFERENCES floors(floor_id) ON DELETE CASCADE
);

-- =========================================================
-- 7. PARKING_SLOTS
-- Chỗ đỗ xe cụ thể
-- =========================================================
CREATE TABLE IF NOT EXISTS parking_slots (
    slot_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    zone_id CHAR(36) NOT NULL,
    slot_name VARCHAR(50) NOT NULL,
    slot_status VARCHAR(30) DEFAULT 'AVAILABLE',
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_slot_name UNIQUE (zone_id, slot_name),
    CONSTRAINT fk_slots_zone FOREIGN KEY (zone_id) REFERENCES zones(zone_id) ON DELETE CASCADE
);

-- =========================================================
-- 8. VEHICLES
-- Xe đăng ký của Driver
-- =========================================================
CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id CHAR(36) NOT NULL,
    vehicle_type_id CHAR(36) NOT NULL,
    plate_number VARCHAR(20) NOT NULL UNIQUE,
    vehicle_color VARCHAR(30),
    brand VARCHAR(50),
    model VARCHAR(50),
    image_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicles_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(vehicle_type_id)
);

-- =========================================================
-- 9. RESERVATIONS
-- Đặt chỗ trước
-- =========================================================
CREATE TABLE IF NOT EXISTS reservations (
    reservation_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id CHAR(36) NOT NULL,
    vehicle_id CHAR(36) NOT NULL,
    slot_id CHAR(36) NOT NULL,
    reservation_code VARCHAR(50) NOT NULL UNIQUE,
    reservation_start DATETIME NOT NULL,
    grace_period_minutes INT DEFAULT 15,
    estimated_fee DECIMAL(10,2) DEFAULT 0,
    reservation_status VARCHAR(30) DEFAULT 'PENDING',
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_reservations_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_reservations_slot FOREIGN KEY (slot_id) REFERENCES parking_slots(slot_id)
);

-- =========================================================
-- 10. TICKETS
-- Vé cho Reservation và Walk-in Guest
-- =========================================================
CREATE TABLE IF NOT EXISTS tickets (
    ticket_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    reservation_id CHAR(36),
    ticket_code VARCHAR(50) NOT NULL UNIQUE,
    is_used BOOLEAN DEFAULT FALSE,
    is_lost BOOLEAN DEFAULT FALSE,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tickets_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id) ON DELETE SET NULL
);

-- =========================================================
-- 11. PARKING_SESSIONS
-- Phiên đỗ xe thực tế
-- =========================================================
CREATE TABLE IF NOT EXISTS parking_sessions (
    session_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    vehicle_id CHAR(36) NOT NULL,
    slot_id CHAR(36) NOT NULL,
    ticket_id CHAR(36),
    reservation_id CHAR(36),
    user_id CHAR(36),
    checkin_type VARCHAR(30),
    checkin_time DATETIME NOT NULL,
    checkout_time DATETIME,
    estimated_fee DECIMAL(10,2) DEFAULT 0,
    total_fee DECIMAL(10,2) DEFAULT 0,
    parking_duration INT DEFAULT 0,
    payment_status VARCHAR(30) DEFAULT 'UNPAID',
    session_status VARCHAR(30) DEFAULT 'ACTIVE',
    note VARCHAR(255),
    guest_name VARCHAR(100),
    guest_phone VARCHAR(20),
    checkin_vehicle_image VARCHAR(255),
    checkout_vehicle_image VARCHAR(255),
    incident_authorized BOOLEAN DEFAULT FALSE,
    created_by CHAR(36),
    updated_by CHAR(36),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id),
    CONSTRAINT fk_sessions_slot FOREIGN KEY (slot_id) REFERENCES parking_slots(slot_id),
    CONSTRAINT fk_sessions_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id),
    CONSTRAINT fk_sessions_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT fk_sessions_updated_by FOREIGN KEY (updated_by) REFERENCES users(user_id)
);

-- =========================================================
-- 12. PAYMENTS
-- Thanh toán phí đỗ xe
-- =========================================================
CREATE TABLE IF NOT EXISTS payments (
    payment_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    session_id CHAR(36) NOT NULL,
    payment_method VARCHAR(40),
    amount DECIMAL(10,2),
    payment_time DATETIME,
    payment_status VARCHAR(30) DEFAULT 'PENDING',
    transaction_code VARCHAR(100),
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_session FOREIGN KEY (session_id) REFERENCES parking_sessions(session_id) ON DELETE CASCADE
);

-- =========================================================
-- 13. PRICING_POLICIES
-- Chính sách giá cho từng loại xe
-- =========================================================
CREATE TABLE IF NOT EXISTS pricing_policies (
    policy_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    vehicle_type_id CHAR(36) NOT NULL,
    policy_name VARCHAR(100) NOT NULL,
    pricing_type VARCHAR(40),
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
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_policy_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(vehicle_type_id)
);

-- =========================================================
-- 14. INCIDENTS
-- Sự cố trong quá trình đỗ xe
-- =========================================================
CREATE TABLE IF NOT EXISTS incidents (
    incident_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    session_id CHAR(36) NOT NULL,
    incident_type VARCHAR(50),
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'OPEN',
    reporter_id VARCHAR(100),
    report_source VARCHAR(20),
    resolution VARCHAR(500),
    resolved_at DATETIME,
    resolved_by VARCHAR(100),
    resolution_action VARCHAR(50),
    verified_plate_number VARCHAR(20),
    verified_ticket_code VARCHAR(50),
    verification_result VARCHAR(20),
    verified_at DATETIME,
    verified_by VARCHAR(100),
    previous_status VARCHAR(20),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incidents_session FOREIGN KEY (session_id) REFERENCES parking_sessions(session_id) ON DELETE CASCADE
);

-- =========================================================
-- 15. BUILDING_RULES
-- Quy tắc riêng cho từng building
-- =========================================================
CREATE TABLE IF NOT EXISTS building_rules (
    rule_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    building_id CHAR(36) NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    rule_value VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_building_rules_building FOREIGN KEY (building_id) REFERENCES buildings(building_id) ON DELETE CASCADE
);

-- =========================================================
-- 16. SYSTEM_CONFIGS
-- Cấu hình hệ thống key-value
-- =========================================================
CREATE TABLE IF NOT EXISTS system_configs (
    config_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    config_key VARCHAR(80) NOT NULL UNIQUE,
    config_value VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 17. AUDIT_LOGS
-- Nhật ký hoạt động (audit trail)
-- =========================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
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
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- SEED DATA - Chèn dữ liệu mẫu để test
-- Lưu ý: Password "123" đã được hash bằng BCrypt
-- =========================================================

-- USERS (4 users: admin, manager, staff, driver)
INSERT IGNORE INTO users (user_id, username, password_hash, full_name, email, role, status) VALUES
('11111111-1111-1111-1111-111111111111', 'admin', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'System Administrator', 'admin@parking.com', 'ROLE_ADMIN', 'ACTIVE'),
('22222222-2222-2222-2222-222222222222', 'manager1', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Parking Manager', 'manager@parking.com', 'ROLE_MANAGER', 'ACTIVE'),
('33333333-3333-3333-3333-333333333333', 'staff1', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Parking Staff', 'staff@parking.com', 'ROLE_STAFF', 'ACTIVE'),
('44444444-4444-4444-4444-444444444444', 'driver1', '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Driver User', 'driver@parking.com', 'ROLE_DRIVER', 'ACTIVE');

-- VEHICLE_TYPES (4 loại xe)
INSERT IGNORE INTO vehicle_types (vehicle_type_id, type_name, size_category, description) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Motorbike', 'SMALL', 'Standard motorbike'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Car', 'MEDIUM', '4-seat or 7-seat car'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'SUV', 'LARGE', 'SUV vehicle'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Truck', 'LARGE', 'Truck vehicle');

-- BUILDING (1 tòa nhà chính)
INSERT IGNORE INTO buildings (building_id, building_name, address, total_floors, operating_start_time, operating_end_time, contact_number, status) VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Main Parking Building', 'District 1, Ho Chi Minh City', 4, '06:00:00', '23:00:00', '0909000000', 'ACTIVE');

-- BUILDING_STAFF (gán staff1 cho building chính)
INSERT IGNORE INTO building_staff (assignment_id, building_id, user_id) VALUES
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333');

-- FLOORS (4 tầng, mỗi tầng 1 loại xe)
INSERT IGNORE INTO floors (floor_id, building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status) VALUES
('f1111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Floor 1 - Car', 1, 30, 'ACTIVE'),
('f2222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Floor 2 - Motorbike', 2, 30, 'ACTIVE'),
('f3333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'Floor 3 - SUV', 3, 20, 'ACTIVE'),
('f4444444-4444-4444-4444-444444444444', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Floor 4 - Truck', 4, 20, 'ACTIVE');

-- ZONES (6 zones: 3 cho F1, 3 cho F2)
INSERT IGNORE INTO zones (zone_id, floor_id, zone_name, max_capacity, status) VALUES
('z1111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'Zone-A-F1', 5, 'ACTIVE'),
('z1111111-1111-1111-1111-111111111112', 'f1111111-1111-1111-1111-111111111111', 'Zone-B-F1', 5, 'ACTIVE'),
('z1111111-1111-1111-1111-111111111113', 'f1111111-1111-1111-1111-111111111111', 'Zone-C-F1', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222221', 'f2222222-2222-2222-2222-222222222222', 'Zone-A-F2', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 'Zone-B-F2', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222223', 'f2222222-2222-2222-2222-222222222222', 'Zone-C-F2', 5, 'ACTIVE');

-- PARKING_SLOTS (30 slots: 5 slots/zone x 6 zones)
INSERT IGNORE INTO parking_slots (slot_id, zone_id, slot_name, slot_status) VALUES
-- Zone-A-F1
('s0000001-0000-0000-0000-000000000001', 'z1111111-1111-1111-1111-111111111111', 'A-F1-01', 'AVAILABLE'),
('s0000001-0000-0000-0000-000000000002', 'z1111111-1111-1111-1111-111111111111', 'A-F1-02', 'AVAILABLE'),
('s0000001-0000-0000-0000-000000000003', 'z1111111-1111-1111-1111-111111111111', 'A-F1-03', 'AVAILABLE'),
('s0000001-0000-0000-0000-000000000004', 'z1111111-1111-1111-1111-111111111111', 'A-F1-04', 'AVAILABLE'),
('s0000001-0000-0000-0000-000000000005', 'z1111111-1111-1111-1111-111111111111', 'A-F1-05', 'AVAILABLE'),
-- Zone-B-F1
('s0000002-0000-0000-0000-000000000001', 'z1111111-1111-1111-1111-111111111112', 'B-F1-01', 'AVAILABLE'),
('s0000002-0000-0000-0000-000000000002', 'z1111111-1111-1111-1111-111111111112', 'B-F1-02', 'AVAILABLE'),
('s0000002-0000-0000-0000-000000000003', 'z1111111-1111-1111-1111-111111111112', 'B-F1-03', 'AVAILABLE'),
('s0000002-0000-0000-0000-000000000004', 'z1111111-1111-1111-1111-111111111112', 'B-F1-04', 'AVAILABLE'),
('s0000002-0000-0000-0000-000000000005', 'z1111111-1111-1111-1111-111111111112', 'B-F1-05', 'AVAILABLE'),
-- Zone-C-F1
('s0000003-0000-0000-0000-000000000001', 'z1111111-1111-1111-1111-111111111113', 'C-F1-01', 'AVAILABLE'),
('s0000003-0000-0000-0000-000000000002', 'z1111111-1111-1111-1111-111111111113', 'C-F1-02', 'AVAILABLE'),
('s0000003-0000-0000-0000-000000000003', 'z1111111-1111-1111-1111-111111111113', 'C-F1-03', 'AVAILABLE'),
('s0000003-0000-0000-0000-000000000004', 'z1111111-1111-1111-1111-111111111113', 'C-F1-04', 'AVAILABLE'),
('s0000003-0000-0000-0000-000000000005', 'z1111111-1111-1111-1111-111111111113', 'C-F1-05', 'AVAILABLE'),
-- Zone-A-F2
('s0000004-0000-0000-0000-000000000001', 'z2222222-2222-2222-2222-222222222221', 'A-F2-01', 'AVAILABLE'),
('s0000004-0000-0000-0000-000000000002', 'z2222222-2222-2222-2222-222222222221', 'A-F2-02', 'AVAILABLE'),
('s0000004-0000-0000-0000-000000000003', 'z2222222-2222-2222-2222-222222222221', 'A-F2-03', 'AVAILABLE'),
('s0000004-0000-0000-0000-000000000004', 'z2222222-2222-2222-2222-222222222221', 'A-F2-04', 'AVAILABLE'),
('s0000004-0000-0000-0000-000000000005', 'z2222222-2222-2222-2222-222222222221', 'A-F2-05', 'AVAILABLE'),
-- Zone-B-F2
('s0000005-0000-0000-0000-000000000001', 'z2222222-2222-2222-2222-222222222222', 'B-F2-01', 'AVAILABLE'),
('s0000005-0000-0000-0000-000000000002', 'z2222222-2222-2222-2222-222222222222', 'B-F2-02', 'AVAILABLE'),
('s0000005-0000-0000-0000-000000000003', 'z2222222-2222-2222-2222-222222222222', 'B-F2-03', 'AVAILABLE'),
('s0000005-0000-0000-0000-000000000004', 'z2222222-2222-2222-2222-222222222222', 'B-F2-04', 'AVAILABLE'),
('s0000005-0000-0000-0000-000000000005', 'z2222222-2222-2222-2222-222222222222', 'B-F2-05', 'AVAILABLE'),
-- Zone-C-F2
('s0000006-0000-0000-0000-000000000001', 'z2222222-2222-2222-2222-222222222223', 'C-F2-01', 'AVAILABLE'),
('s0000006-0000-0000-0000-000000000002', 'z2222222-2222-2222-2222-222222222223', 'C-F2-02', 'AVAILABLE'),
('s0000006-0000-0000-0000-000000000003', 'z2222222-2222-2222-2222-222222222223', 'C-F2-03', 'AVAILABLE'),
('s0000006-0000-0000-0000-000000000004', 'z2222222-2222-2222-2222-222222222223', 'C-F2-04', 'AVAILABLE'),
('s0000006-0000-0000-0000-000000000005', 'z2222222-2222-2222-2222-222222222223', 'C-F2-05', 'AVAILABLE');

-- PRICING POLICIES (chính sách giá cho mỗi loại xe)
INSERT IGNORE INTO pricing_policies (policy_id, vehicle_type_id, policy_name, pricing_type, base_price, hourly_rate, max_hours, status) VALUES
('p0000001-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Motorbike Standard', 'HOURLY', 5000.00, 3000.00, 24, 'ACTIVE'),
('p0000002-0000-0000-0000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Car Standard', 'HOURLY', 20000.00, 20000.00, 24, 'ACTIVE'),
('p0000003-0000-0000-0000-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'SUV Standard', 'HOURLY', 25000.00, 25000.00, 24, 'ACTIVE'),
('p0000004-0000-0000-0000-000000000004', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Truck Standard', 'HOURLY', 30000.00, 30000.00, 24, 'ACTIVE');

-- VEHICLE (1 xe mẫu cho driver1)
INSERT IGNORE INTO vehicles (vehicle_id, user_id, vehicle_type_id, plate_number, vehicle_color, brand, model, status) VALUES
('vv111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444444', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '51A-12345', 'White', 'Toyota', 'Vios', 'ACTIVE');

-- SYSTEM CONFIGS (cấu hình mặc định)
INSERT IGNORE INTO system_configs (config_key, config_value, description) VALUES
('OCR_PLATE_API_URL', 'https://api.platerecognizer.com/v1/plate-reader/', 'URL của OCR service nhận diện biển số'),
('DEFAULT_GRACE_PERIOD_MINUTES', '15', 'Thời gian grace period mặc định cho reservation'),
('MAX_PARKING_HOURS', '24', 'Số giờ đỗ tối đa trong 1 session'),
('NOTIFICATION_ENABLED', 'true', 'Bật/tắt thông báo trong hệ thống');

-- =========================================================
-- VERIFICATION - Kiểm tra kết quả
-- =========================================================
SELECT '=== INIT COMPLETED ===' AS status;

SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = DATABASE();

SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'buildings', COUNT(*) FROM buildings
UNION ALL SELECT 'building_staff', COUNT(*) FROM building_staff
UNION ALL SELECT 'vehicle_types', COUNT(*) FROM vehicle_types
UNION ALL SELECT 'floors', COUNT(*) FROM floors
UNION ALL SELECT 'zones', COUNT(*) FROM zones
UNION ALL SELECT 'parking_slots', COUNT(*) FROM parking_slots
UNION ALL SELECT 'vehicles', COUNT(*) FROM vehicles
UNION ALL SELECT 'pricing_policies', COUNT(*) FROM pricing_policies
UNION ALL SELECT 'system_configs', COUNT(*) FROM system_configs;
