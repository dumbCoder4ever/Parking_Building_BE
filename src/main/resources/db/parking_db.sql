-- =============================================================================
-- PARKING MANAGEMENT SYSTEM — MySQL schema + seed data
-- Project : Parking_Building_BE
-- Usage   : mysql -u root -p < src/main/resources/db/parking_db.sql
--           (Windows CMD / PowerShell — chạy từ thư mục gốc project)
-- =============================================================================

DROP DATABASE IF EXISTS parking_db;

CREATE DATABASE parking_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE parking_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- USERS
-- =============================================================================

CREATE TABLE users (
    user_id       CHAR(36)     NOT NULL DEFAULT (UUID()),
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) DEFAULT NULL,
    phone_number  VARCHAR(20)  DEFAULT NULL,
    email         VARCHAR(100) DEFAULT NULL,
    avatar_url    VARCHAR(255) DEFAULT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at    DATETIME     DEFAULT NULL,
    last_login    DATETIME     DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- BUILDINGS
-- =============================================================================

CREATE TABLE buildings (
    building_id          CHAR(36)     NOT NULL DEFAULT (UUID()),
    building_name        VARCHAR(100) NOT NULL,
    address              VARCHAR(255) DEFAULT NULL,
    total_floors         INT          DEFAULT NULL,
    operating_start_time TIME         DEFAULT NULL,
    operating_end_time   TIME         DEFAULT NULL,
    contact_number       VARCHAR(20)  DEFAULT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- BUILDING STAFF
-- =============================================================================

CREATE TABLE building_staff (
    assignment_id CHAR(36) NOT NULL DEFAULT (UUID()),
    building_id   CHAR(36) NOT NULL,
    user_id       CHAR(36) NOT NULL,
    assigned_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assignment_id),
    UNIQUE KEY uq_building_staff (building_id, user_id),
    CONSTRAINT fk_building_staff_building
        FOREIGN KEY (building_id) REFERENCES buildings (building_id) ON DELETE CASCADE,
    CONSTRAINT fk_building_staff_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- VEHICLE TYPES
-- =============================================================================

CREATE TABLE vehicle_types (
    vehicle_type_id CHAR(36)    NOT NULL DEFAULT (UUID()),
    type_name       VARCHAR(50) NOT NULL,
    size_category   VARCHAR(30) DEFAULT NULL,
    description     VARCHAR(255) DEFAULT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vehicle_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FLOORS
-- =============================================================================

CREATE TABLE floors (
    floor_id           CHAR(36)    NOT NULL DEFAULT (UUID()),
    building_id        CHAR(36)    NOT NULL,
    vehicle_type_id    CHAR(36)    NOT NULL,
    floor_name         VARCHAR(50) DEFAULT NULL,
    floor_level        INT         DEFAULT NULL,
    max_capacity       INT         DEFAULT 0,
    current_occupancy  INT         DEFAULT 0,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (floor_id),
    UNIQUE KEY uq_building_vehicle_type (building_id, vehicle_type_id),
    CONSTRAINT fk_floors_building
        FOREIGN KEY (building_id) REFERENCES buildings (building_id) ON DELETE CASCADE,
    CONSTRAINT fk_floors_vehicle_type
        FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types (vehicle_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- ZONES
-- =============================================================================

CREATE TABLE zones (
    zone_id           CHAR(36)    NOT NULL DEFAULT (UUID()),
    floor_id          CHAR(36)    NOT NULL,
    zone_name         VARCHAR(50) DEFAULT NULL,
    max_capacity      INT         DEFAULT 0,
    current_occupancy INT         DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (zone_id),
    CONSTRAINT fk_zones_floor
        FOREIGN KEY (floor_id) REFERENCES floors (floor_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- PARKING SLOTS
-- =============================================================================

CREATE TABLE parking_slots (
    slot_id     CHAR(36)    NOT NULL DEFAULT (UUID()),
    zone_id     CHAR(36)    NOT NULL,
    slot_name   VARCHAR(50) DEFAULT NULL,
    slot_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    note        VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (slot_id),
    UNIQUE KEY uq_slot_name (zone_id, slot_name),
    CONSTRAINT fk_slots_zone
        FOREIGN KEY (zone_id) REFERENCES zones (zone_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- VEHICLES
-- =============================================================================

CREATE TABLE vehicles (
    vehicle_id      CHAR(36)    NOT NULL DEFAULT (UUID()),
    user_id         CHAR(36)    NOT NULL,
    vehicle_type_id CHAR(36)    NOT NULL,
    plate_number    VARCHAR(20) NOT NULL,
    vehicle_color   VARCHAR(30) DEFAULT NULL,
    brand           VARCHAR(50) DEFAULT NULL,
    model           VARCHAR(50) DEFAULT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (vehicle_id),
    UNIQUE KEY uq_vehicles_plate (plate_number),
    CONSTRAINT fk_vehicles_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicles_type
        FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types (vehicle_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- RESERVATIONS
-- =============================================================================

CREATE TABLE reservations (
    reservation_id        CHAR(36)      NOT NULL DEFAULT (UUID()),
    user_id               CHAR(36)      NOT NULL,
    vehicle_id            CHAR(36)      NOT NULL,
    slot_id               CHAR(36)      NOT NULL,
    reservation_code      VARCHAR(50)   DEFAULT NULL,
    reservation_start     DATETIME      NOT NULL,
    reservation_end       DATETIME      NOT NULL,
    grace_period_minutes  INT           NOT NULL DEFAULT 15,
    estimated_fee         DECIMAL(10,2) DEFAULT 0,
    reservation_status    VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    note                  VARCHAR(255)  DEFAULT NULL,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uq_reservations_code (reservation_code),
    CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_reservations_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles (vehicle_id),
    CONSTRAINT fk_reservations_slot
        FOREIGN KEY (slot_id) REFERENCES parking_slots (slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TICKETS
-- =============================================================================

CREATE TABLE tickets (
    ticket_id      CHAR(36)    NOT NULL DEFAULT (UUID()),
    reservation_id CHAR(36)    DEFAULT NULL,
    ticket_code    VARCHAR(50) DEFAULT NULL,
    is_used        TINYINT(1)  NOT NULL DEFAULT 0,
    is_lost        TINYINT(1)  NOT NULL DEFAULT 0,
    status         VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    issued_at      DATETIME    DEFAULT NULL,
    expired_at     DATETIME    DEFAULT NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_id),
    UNIQUE KEY uq_tickets_reservation (reservation_id),
    UNIQUE KEY uq_tickets_code (ticket_code),
    CONSTRAINT fk_tickets_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- PARKING SESSIONS
-- =============================================================================

CREATE TABLE parking_sessions (
    session_id      CHAR(36)      NOT NULL DEFAULT (UUID()),
    vehicle_id        CHAR(36)      NOT NULL,
    slot_id           CHAR(36)      NOT NULL,
    ticket_id         CHAR(36)      DEFAULT NULL,
    reservation_id    CHAR(36)      DEFAULT NULL,
    checkin_time      DATETIME      NOT NULL,
    checkout_time     DATETIME      DEFAULT NULL,
    estimated_fee     DECIMAL(10,2) DEFAULT 0,
    total_fee         DECIMAL(10,2) DEFAULT 0,
    parking_duration  INT           DEFAULT 0,
    payment_status    VARCHAR(30)   NOT NULL DEFAULT 'UNPAID',
    session_status    VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    note              VARCHAR(255)  DEFAULT NULL,
    created_by        CHAR(36)      DEFAULT NULL,
    updated_by        CHAR(36)      DEFAULT NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    CONSTRAINT fk_sessions_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES vehicles (vehicle_id),
    CONSTRAINT fk_sessions_slot
        FOREIGN KEY (slot_id) REFERENCES parking_slots (slot_id),
    CONSTRAINT fk_sessions_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (ticket_id),
    CONSTRAINT fk_sessions_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id),
    CONSTRAINT fk_sessions_created_by
        FOREIGN KEY (created_by) REFERENCES users (user_id),
    CONSTRAINT fk_sessions_updated_by
        FOREIGN KEY (updated_by) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- PAYMENTS
-- =============================================================================

CREATE TABLE payments (
    payment_id       CHAR(36)      NOT NULL DEFAULT (UUID()),
    session_id       CHAR(36)      NOT NULL,
    payment_method   VARCHAR(40)   NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    payment_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    payment_status   VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    transaction_code VARCHAR(100)  DEFAULT NULL,
    note             VARCHAR(255)  DEFAULT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payments_session
        FOREIGN KEY (session_id) REFERENCES parking_sessions (session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- PRICING POLICIES
-- =============================================================================

CREATE TABLE pricing_policies (
    policy_id       CHAR(36)      NOT NULL DEFAULT (UUID()),
    vehicle_type_id CHAR(36)      NOT NULL,
    policy_name     VARCHAR(100)  DEFAULT NULL,
    pricing_type    VARCHAR(40)   DEFAULT NULL,
    base_price      DECIMAL(10,2) DEFAULT 0,
    hourly_rate     DECIMAL(10,2) DEFAULT 0,
    max_hours       INT           DEFAULT 24,
    effective_from  DATETIME      DEFAULT NULL,
    effective_to    DATETIME      DEFAULT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_id),
    CONSTRAINT fk_policy_vehicle_type
        FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types (vehicle_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- INCIDENTS
-- =============================================================================

CREATE TABLE incidents (
    incident_id   CHAR(36)     NOT NULL DEFAULT (UUID()),
    session_id    CHAR(36)     NOT NULL,
    incident_type VARCHAR(50)  DEFAULT NULL,
    description   VARCHAR(500) DEFAULT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (incident_id),
    CONSTRAINT fk_incidents_session
        FOREIGN KEY (session_id) REFERENCES parking_sessions (session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- SEED: USERS
-- Passwords (BCrypt):
--   admin, manager1, staff1 -> 123
--   driver1                 -> 1
-- Login API uses email + password (POST /api/auth/login)
-- =============================================================================

INSERT INTO users (user_id, username, password_hash, full_name, email, role, status) VALUES
(
    '11111111-1111-1111-1111-111111111111',
    'admin',
    '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea',
    'System Administrator',
    'admin@example.com',
    'ROLE_ADMIN',
    'ACTIVE'
),
(
    '22222222-2222-2222-2222-222222222222',
    'manager1',
    '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea',
    'Parking Manager',
    'manager1@example.com',
    'ROLE_MANAGER',
    'ACTIVE'
),
(
    '33333333-3333-3333-3333-333333333333',
    'staff1',
    '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea',
    'Parking Staff',
    'staff1@example.com',
    'ROLE_STAFF',
    'ACTIVE'
),
(
    '44444444-4444-4444-4444-444444444444',
    'driver1',
    '$2a$10$3R/XmppgsdJqbVcjBda5m.qNC8S.4u8cSb9n9yYoTuCg8SFWn/r0y',
    'Driver User',
    'driver1@example.com',
    'ROLE_DRIVER',
    'ACTIVE'
);

-- =============================================================================
-- SEED: VEHICLE TYPES
-- =============================================================================

INSERT INTO vehicle_types (vehicle_type_id, type_name, size_category, description) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Motorbike', 'SMALL',  'Standard motorbike'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Car',       'MEDIUM', '4-seat or 7-seat car'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'SUV',       'LARGE',  'Sample SUV type'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Truck',     'LARGE',  'Sample truck type');

-- =============================================================================
-- SEED: BUILDING + STAFF
-- =============================================================================

INSERT INTO buildings (
    building_id,
    building_name,
    address,
    total_floors,
    operating_start_time,
    operating_end_time,
    contact_number,
    status
) VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'Main Parking Building',
    'District 1, Ho Chi Minh City',
    4,
    '06:00:00',
    '23:00:00',
    '0909000000',
    'ACTIVE'
);

INSERT INTO building_staff (assignment_id, building_id, user_id) VALUES
(UUID(), 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333');

-- =============================================================================
-- SEED: FLOORS, ZONES, SLOTS
-- =============================================================================

INSERT INTO floors (floor_id, building_id, vehicle_type_id, floor_name, floor_level, max_capacity, status) VALUES
('f1111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'Floor 1 - Car',       1, 10, 'ACTIVE'),
('f2222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'Floor 2 - Motorbike', 2, 10, 'ACTIVE'),
('f3333333-3333-3333-3333-333333333333', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'Floor 3 - SUV',       3, 10, 'ACTIVE'),
('f4444444-4444-4444-4444-444444444444', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'Floor 4 - Truck',     4, 10, 'ACTIVE');

INSERT INTO zones (zone_id, floor_id, zone_name, max_capacity, status) VALUES
('z1111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'Zone A', 5, 'ACTIVE'),
('z1111111-1111-1111-1111-111111111112', 'f1111111-1111-1111-1111-111111111111', 'Zone B', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 'Zone A', 5, 'ACTIVE'),
('z2222222-2222-2222-2222-222222222223', 'f2222222-2222-2222-2222-222222222222', 'Zone B', 5, 'ACTIVE');

INSERT INTO parking_slots (zone_id, slot_name, slot_status)
SELECT z.zone_id, CONCAT('S', LPAD(n.n, 2, '0')), 'AVAILABLE'
FROM zones z
JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) n
WHERE z.zone_id IN (
    'z1111111-1111-1111-1111-111111111111',
    'z1111111-1111-1111-1111-111111111112',
    'z2222222-2222-2222-2222-222222222222',
    'z2222222-2222-2222-2222-222222222223'
);

-- =============================================================================
-- SEED: PRICING POLICIES (hourly model used by PricingService)
-- Formula: base_price + hourly_rate * (hours - 1), capped by max_hours
-- =============================================================================

INSERT INTO pricing_policies (
    policy_id,
    vehicle_type_id,
    policy_name,
    pricing_type,
    base_price,
    hourly_rate,
    max_hours,
    status
) VALUES
(
    'p1111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    'Motorbike Standard',
    'HOURLY',
    5000.00,
    3000.00,
    24,
    'ACTIVE'
),
(
    'p2222222-2222-2222-2222-222222222222',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    'Car Standard',
    'HOURLY',
    20000.00,
    10000.00,
    24,
    'ACTIVE'
);

-- =============================================================================
-- SEED: SAMPLE DRIVER VEHICLE
-- =============================================================================

INSERT INTO vehicles (
    vehicle_id,
    user_id,
    vehicle_type_id,
    plate_number,
    vehicle_color,
    brand,
    model,
    status
) VALUES (
    'v1111111-1111-1111-1111-111111111111',
    '44444444-4444-4444-4444-444444444444',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    '51A-12345',
    'White',
    'Toyota',
    'Vios',
    'ACTIVE'
);

-- =============================================================================
-- DONE
-- =============================================================================
