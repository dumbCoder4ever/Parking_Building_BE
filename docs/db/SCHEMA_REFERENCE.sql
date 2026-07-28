-- =========================================================
-- PARKING MANAGEMENT SYSTEM - SCHEMA REFERENCE
-- File tham khảo để thiết kế ERD
-- Chỉ liệt kê các bảng + các cột hiện có trong DB
-- KHÔNG có DROP, KHÔNG có INSERT seed data
-- =========================================================

-- =========================================================
-- 1. USERS
-- Quản lý tất cả user: Admin, Manager, Staff, Driver
-- =========================================================
CREATE TABLE users (
    user_id CHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    phone_number VARCHAR(20),
    email VARCHAR(100) UNIQUE,
    avatar_url VARCHAR(255),
    role VARCHAR(20) NOT NULL,                -- ROLE_ADMIN | ROLE_MANAGER | ROLE_STAFF | ROLE_DRIVER
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE | BANNED
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
CREATE TABLE buildings (
    building_id CHAR(36) PRIMARY KEY,
    building_name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    total_floors INT,
    operating_start_time TIME,
    operating_end_time TIME,
    contact_number VARCHAR(20),
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE | MAINTENANCE
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 3. VEHICLE_TYPES
-- Loại xe (Motorbike, Car, SUV, Truck)
-- =========================================================
CREATE TABLE vehicle_types (
    vehicle_type_id CHAR(36) PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL,
    size_category VARCHAR(30),
    description VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 4. BUILDING_STAFF
-- Phân công staff cho từng building
-- (Many-to-Many: users <-> buildings)
-- =========================================================
CREATE TABLE building_staff (
    assignment_id CHAR(36) PRIMARY KEY,
    building_id CHAR(36) NOT NULL,            -- FK -> buildings
    user_id CHAR(36) NOT NULL,                -- FK -> users
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_building_staff (building_id, user_id)
);

-- =========================================================
-- 5. FLOORS
-- Tầng trong tòa nhà, mỗi tầng cho 1 loại xe
-- =========================================================
CREATE TABLE floors (
    floor_id CHAR(36) PRIMARY KEY,
    building_id CHAR(36) NOT NULL,            -- FK -> buildings
    vehicle_type_id CHAR(36) NOT NULL,        -- FK -> vehicle_types
    floor_name VARCHAR(50) NOT NULL,
    floor_level INT NOT NULL,
    max_capacity INT DEFAULT 0,
    current_occupancy INT DEFAULT 0,
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE | MAINTENANCE
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 6. ZONES
-- Khu vực trong tầng
-- =========================================================
CREATE TABLE zones (
    zone_id CHAR(36) PRIMARY KEY,
    floor_id CHAR(36) NOT NULL,               -- FK -> floors
    zone_name VARCHAR(50) NOT NULL,
    max_capacity INT DEFAULT 0,
    current_occupancy INT DEFAULT 0,
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE | FULL | MAINTENANCE
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 7. PARKING_SLOTS
-- Chỗ đỗ xe cụ thể
-- =========================================================
CREATE TABLE parking_slots (
    slot_id CHAR(36) PRIMARY KEY,
    zone_id CHAR(36) NOT NULL,                -- FK -> zones
    slot_name VARCHAR(50) NOT NULL,
    slot_status VARCHAR(30) DEFAULT 'AVAILABLE',    -- AVAILABLE | RESERVED | OCCUPIED | MAINTENANCE
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_slot_name (zone_id, slot_name)
);

-- =========================================================
-- 8. VEHICLES
-- Xe đăng ký của Driver
-- =========================================================
CREATE TABLE vehicles (
    vehicle_id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,                -- FK -> users (driver)
    vehicle_type_id CHAR(36) NOT NULL,        -- FK -> vehicle_types
    plate_number VARCHAR(20) NOT NULL UNIQUE,
    vehicle_color VARCHAR(30),
    brand VARCHAR(50),
    model VARCHAR(50),
    image_url VARCHAR(255),
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE | BLOCKED
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 9. RESERVATIONS
-- Đặt chỗ trước (Driver đặt slot trước khi đến)
-- =========================================================
CREATE TABLE reservations (
    reservation_id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,                -- FK -> users (driver đặt)
    vehicle_id CHAR(36) NOT NULL,             -- FK -> vehicles
    slot_id CHAR(36) NOT NULL,                -- FK -> parking_slots
    reservation_code VARCHAR(50) NOT NULL UNIQUE,
    reservation_start DATETIME NOT NULL,
    grace_period_minutes INT DEFAULT 15,
    estimated_fee DECIMAL(10,2) DEFAULT 0,
    reservation_status VARCHAR(30) DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | CANCELLED | EXPIRED | COMPLETED
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 10. TICKETS
-- Vé - cấp cho cả Reservation và Walk-in Guest
-- =========================================================
CREATE TABLE tickets (
    ticket_id CHAR(36) PRIMARY KEY,
    reservation_id CHAR(36),                  -- FK -> reservations (NULL nếu là walk-in guest)
    ticket_code VARCHAR(50) NOT NULL UNIQUE,
    is_used BOOLEAN DEFAULT FALSE,
    is_lost BOOLEAN DEFAULT FALSE,
    status VARCHAR(30) DEFAULT 'ACTIVE',      -- ACTIVE | USED | EXPIRED | LOST
    issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 11. PARKING_SESSIONS
-- Phiên đỗ xe thực tế (check-in -> check-out)
-- =========================================================
CREATE TABLE parking_sessions (
    session_id CHAR(36) PRIMARY KEY,
    vehicle_id CHAR(36) NOT NULL,             -- FK -> vehicles
    slot_id CHAR(36) NOT NULL,                -- FK -> parking_slots
    ticket_id CHAR(36),                       -- FK -> tickets
    reservation_id CHAR(36),                  -- FK -> reservations
    user_id CHAR(36),                         -- FK -> users (NULL nếu là guest)
    checkin_type VARCHAR(30),                 -- RESERVATION | WALK_IN_GUEST | DRIVER_NO_RESERVATION
    checkin_time DATETIME NOT NULL,
    checkout_time DATETIME,
    estimated_fee DECIMAL(10,2) DEFAULT 0,
    total_fee DECIMAL(10,2) DEFAULT 0,
    parking_duration INT DEFAULT 0,
    payment_status VARCHAR(30) DEFAULT 'UNPAID',  -- UNPAID | PAID | FAILED
    session_status VARCHAR(30) DEFAULT 'ACTIVE',  -- ACTIVE | PENDING_PAYMENT | PENDING_EXIT | COMPLETED | CANCELLED
    note VARCHAR(255),
    guest_name VARCHAR(100),
    guest_phone VARCHAR(20),
    checkin_vehicle_image VARCHAR(255),
    checkout_vehicle_image VARCHAR(255),
    incident_authorized BOOLEAN DEFAULT FALSE,
    created_by CHAR(36),                      -- FK -> users (staff thực hiện check-in)
    updated_by CHAR(36),                      -- FK -> users
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 12. PAYMENTS
-- Thanh toán phí đỗ xe
-- =========================================================
CREATE TABLE payments (
    payment_id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,             -- FK -> parking_sessions
    payment_method VARCHAR(40),               -- CASH | BANKING | MOMO | VNPAY | PAYOS
    amount DECIMAL(10,2),
    payment_time DATETIME,
    payment_status VARCHAR(30) DEFAULT 'PENDING',  -- PENDING | PAID | CONFIRMED | SUCCESS | FAILED
    transaction_code VARCHAR(100),
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 13. PRICING_POLICIES
-- Chính sách giá cho từng loại xe
-- Hỗ trợ nhiều mô hình: HOURLY, DAILY, OVERNIGHT, TIERED
-- =========================================================
CREATE TABLE pricing_policies (
    policy_id CHAR(36) PRIMARY KEY,
    vehicle_type_id CHAR(36) NOT NULL,        -- FK -> vehicle_types
    policy_name VARCHAR(100) NOT NULL,
    pricing_type VARCHAR(40),                 -- HOURLY | DAILY | OVERNIGHT | TIERED
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
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 14. INCIDENTS
-- Sự cố phát sinh trong lúc đỗ xe
-- (mất vé, sai xe, quá giờ, không thanh toán...)
-- =========================================================
CREATE TABLE incidents (
    incident_id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,             -- FK -> parking_sessions
    incident_type VARCHAR(50),                -- LOST_TICKET | WRONG_VEHICLE | OVERTIME | UNPAID | OTHER
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'OPEN',        -- OPEN | PROCESSING | RESOLVED
    reporter_id VARCHAR(100),
    report_source VARCHAR(20),
    resolution VARCHAR(500),
    resolved_at DATETIME,
    resolved_by VARCHAR(100),
    resolution_action VARCHAR(50),
    verified_plate_number VARCHAR(20),
    verified_ticket_code VARCHAR(50),
    verification_result VARCHAR(20),          -- MATCH | MISMATCH | PENDING
    verified_at DATETIME,
    verified_by VARCHAR(100),
    previous_status VARCHAR(20),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 15. BUILDING_RULES
-- Quy tắc riêng cho từng tòa nhà (giờ hoạt động, curfew...)
-- =========================================================
CREATE TABLE building_rules (
    rule_id CHAR(36) PRIMARY KEY,
    building_id CHAR(36) NOT NULL,            -- FK -> buildings
    rule_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    rule_value VARCHAR(200),
    status VARCHAR(20) NOT NULL,              -- ACTIVE | INACTIVE
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 16. SYSTEM_CONFIGS
-- Cấu hình hệ thống dạng key-value
-- =========================================================
CREATE TABLE system_configs (
    config_id CHAR(36) PRIMARY KEY,
    config_key VARCHAR(80) NOT NULL UNIQUE,
    config_value VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =========================================================
-- 17. AUDIT_LOGS
-- Nhật ký hoạt động (audit trail) cho mọi thay đổi
-- =========================================================
CREATE TABLE audit_logs (
    log_id CHAR(36) PRIMARY KEY,
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
-- TỔNG KẾT: 17 BẢNG CHÍNH + CHECKOUT_REQUESTS = 18 BẢNG
-- =========================================================
-- Nếu Railway bạn đang có 19 bảng thì bảng thứ 18 và 19 có thể là:
--   - checkout_requests (yêu cầu check-out)
--   - notifications  (thông báo)
--   - hoặc bảng phụ khác
--
-- Nếu muốn thêm các bảng đó vào file tham khảo, vui lòng cho mình
-- xem screenshot/script `SHOW TABLES;` từ Railway để bổ sung chính xác.
-- =========================================================
