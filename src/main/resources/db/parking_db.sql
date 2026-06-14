    -- =========================================================
    -- PARKING MANAGEMENT SYSTEM
    -- MYSQL VERSION
    -- =========================================================

    DROP DATABASE IF EXISTS parking_db;

    CREATE DATABASE parking_db;

    USE parking_db;

    -- =========================================================
    -- USERS
    -- =========================================================

    CREATE TABLE users (
                           user_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                           username VARCHAR(50) NOT NULL UNIQUE,

                           password_hash VARCHAR(255) NOT NULL,

                           full_name VARCHAR(100),

                           phone_number VARCHAR(20),

                           email VARCHAR(100),

                           avatar_url VARCHAR(255),

                           role ENUM(
            'ROLE_ADMIN',
            'ROLE_MANAGER',
            'ROLE_STAFF',
            'ROLE_DRIVER'
        ) NOT NULL,

                           status ENUM(
            'ACTIVE',
            'INACTIVE',
            'BANNED'
        ) DEFAULT 'ACTIVE',

                           is_active BOOLEAN DEFAULT TRUE,

                           is_deleted TINYINT(1) NOT NULL DEFAULT 0,

                           deleted_at DATETIME,

                           last_login DATETIME,

                           created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                           updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP
    );

    -- =========================================================
    -- BUILDINGS
    -- =========================================================

    CREATE TABLE buildings (
                               building_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                               building_name VARCHAR(100) NOT NULL,

                               address VARCHAR(255) NOT NULL,

                               total_floors INT NOT NULL,

                               operating_start_time TIME,

                               operating_end_time TIME,

                               contact_number VARCHAR(20),

                               status ENUM(
            'ACTIVE',
            'INACTIVE',
            'MAINTENANCE'
        ) DEFAULT 'ACTIVE',

                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                               updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP
    );

    -- =========================================================
    -- BUILDING STAFF (manager assigns ROLE_STAFF to buildings)
    -- =========================================================

    CREATE TABLE building_staff (
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

    -- =========================================================
    -- VEHICLE TYPES
    -- =========================================================

    CREATE TABLE vehicle_types (
                                   vehicle_type_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                                   type_name VARCHAR(50) NOT NULL,

                                   size_category VARCHAR(30),

                                   description VARCHAR(255),

                                   created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    -- =========================================================
    -- FLOORS
    -- =========================================================

    CREATE TABLE floors (
                            floor_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                            building_id CHAR(36) NOT NULL,

                            vehicle_type_id CHAR(36) NOT NULL,

                            floor_name VARCHAR(50) NOT NULL,

                            floor_level INT NOT NULL,

                            max_capacity INT DEFAULT 0,

                            current_occupancy INT DEFAULT 0,

                            status ENUM(
            'ACTIVE',
            'INACTIVE',
            'MAINTENANCE'
        ) DEFAULT 'ACTIVE',

                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

                            CONSTRAINT fk_floors_building
                                FOREIGN KEY (building_id)
                                    REFERENCES buildings(building_id)
                                    ON DELETE CASCADE,

                            CONSTRAINT fk_floors_vehicle_type
                                FOREIGN KEY (vehicle_type_id)
                                    REFERENCES vehicle_types(vehicle_type_id),

                            CONSTRAINT uq_building_vehicle_type
                                UNIQUE (building_id, vehicle_type_id)
    );

    -- =========================================================
    -- ZONES
    -- =========================================================

    CREATE TABLE zones (
                           zone_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                           floor_id CHAR(36) NOT NULL,

                           zone_name VARCHAR(50) NOT NULL,

                           max_capacity INT DEFAULT 0,

                           current_occupancy INT DEFAULT 0,

                           status ENUM(
            'ACTIVE',
            'INACTIVE',
            'FULL',
            'MAINTENANCE'
        ) DEFAULT 'ACTIVE',

                           created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                           updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,

                           CONSTRAINT fk_zones_floor
                               FOREIGN KEY (floor_id)
                                   REFERENCES floors(floor_id)
                                   ON DELETE CASCADE
    );

    -- =========================================================
    -- PARKING SLOTS
    -- =========================================================

    CREATE TABLE parking_slots (
                                   slot_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                                   zone_id CHAR(36) NOT NULL,

                                   slot_name VARCHAR(50) NOT NULL,

                                   slot_status ENUM(
            'AVAILABLE',
            'RESERVED',
            'OCCUPIED',
            'MAINTENANCE'
        ) DEFAULT 'AVAILABLE',

                                   note VARCHAR(255),

                                   created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                                   updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP,

                                   CONSTRAINT uq_slot_name
                                       UNIQUE (zone_id, slot_name),

                                   CONSTRAINT fk_slots_zone
                                       FOREIGN KEY (zone_id)
                                           REFERENCES zones(zone_id)
                                           ON DELETE CASCADE
    );

    -- =========================================================
    -- VEHICLES
    -- =========================================================

    CREATE TABLE vehicles (
                              vehicle_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                              user_id CHAR(36) NOT NULL,

                              vehicle_type_id CHAR(36) NOT NULL,

                              plate_number VARCHAR(20) NOT NULL UNIQUE,

                              vehicle_color VARCHAR(30),

                              brand VARCHAR(50),

                              model VARCHAR(50),

                              status ENUM(
            'ACTIVE',
            'INACTIVE',
            'BLOCKED'
        ) DEFAULT 'ACTIVE',

                              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                              updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

                              CONSTRAINT fk_vehicles_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users(user_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_vehicles_type
                                  FOREIGN KEY (vehicle_type_id)
                                      REFERENCES vehicle_types(vehicle_type_id)
    );

    -- =========================================================
    -- RESERVATIONS
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

                                  reservation_status ENUM(
            'PENDING',
            'APPROVED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED',
            'COMPLETED'
        ) DEFAULT 'PENDING',

                                  note VARCHAR(255),

                                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                                  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_reservations_user
                                      FOREIGN KEY (user_id)
                                          REFERENCES users(user_id),

                                  CONSTRAINT fk_reservations_vehicle
                                      FOREIGN KEY (vehicle_id)
                                          REFERENCES vehicles(vehicle_id),

                                  CONSTRAINT fk_reservations_slot
                                      FOREIGN KEY (slot_id)
                                          REFERENCES parking_slots(slot_id)
    );

    -- =========================================================
    -- TICKETS
    -- =========================================================

    CREATE TABLE tickets (
                             ticket_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                             reservation_id CHAR(36) NOT NULL UNIQUE,

                             ticket_code VARCHAR(50) NOT NULL UNIQUE,

                             is_used BOOLEAN DEFAULT FALSE,

                             is_lost BOOLEAN DEFAULT FALSE,

                             status ENUM(
            'ACTIVE',
            'USED',
            'EXPIRED',
            'LOST'
        ) DEFAULT 'ACTIVE',

                             issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                             expired_at DATETIME,

                             created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_tickets_reservation
                                 FOREIGN KEY (reservation_id)
                                     REFERENCES reservations(reservation_id)
                                     ON DELETE CASCADE
    );

    -- =========================================================
    -- PARKING SESSIONS
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

                                      payment_status ENUM(
            'UNPAID',
            'PAID',
            'FAILED'
        ) DEFAULT 'UNPAID',

                                      session_status ENUM(
            'ACTIVE',
            'PENDING_PAYMENT',
            'PENDING_EXIT',
            'COMPLETED',
            'CANCELLED'
        ) DEFAULT 'ACTIVE',

                                      note VARCHAR(255),

                                      created_by CHAR(36),

                                      updated_by CHAR(36),

                                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                                      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

                                      CONSTRAINT fk_sessions_vehicle
                                          FOREIGN KEY (vehicle_id)
                                              REFERENCES vehicles(vehicle_id),

                                      CONSTRAINT fk_sessions_slot
                                          FOREIGN KEY (slot_id)
                                              REFERENCES parking_slots(slot_id),

                                      CONSTRAINT fk_sessions_ticket
                                          FOREIGN KEY (ticket_id)
                                              REFERENCES tickets(ticket_id),

                                      CONSTRAINT fk_sessions_reservation
                                          FOREIGN KEY (reservation_id)
                                              REFERENCES reservations(reservation_id),

                                      CONSTRAINT fk_sessions_created_by
                                          FOREIGN KEY (created_by)
                                              REFERENCES users(user_id),

                                      CONSTRAINT fk_sessions_updated_by
                                          FOREIGN KEY (updated_by)
                                              REFERENCES users(user_id)
    );

    -- =========================================================
    -- PAYMENTS
    -- =========================================================

    CREATE TABLE payments (
                              payment_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                              session_id CHAR(36) NOT NULL,

                              payment_method ENUM(
            'CASH',
            'BANKING',
            'MOMO',
            'VNPAY',
            'PAYOS'
        ) NOT NULL,

                              amount DECIMAL(10,2) NOT NULL,

                              payment_time DATETIME DEFAULT CURRENT_TIMESTAMP,

                              payment_status ENUM(
            'PENDING',
            'PAID',
            'CONFIRMED',
            'SUCCESS',
            'FAILED'
        ) DEFAULT 'PENDING',

                              transaction_code VARCHAR(100),

                              note VARCHAR(255),

                              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_payments_session
                                  FOREIGN KEY (session_id)
                                      REFERENCES parking_sessions(session_id)
                                      ON DELETE CASCADE
    );

    -- =========================================================
    -- PRICING POLICIES
    -- =========================================================

    CREATE TABLE pricing_policies (
                                      policy_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                                      vehicle_type_id CHAR(36) NOT NULL,

                                      policy_name VARCHAR(100) NOT NULL,

                                      pricing_type ENUM(
            'HOURLY',
            'DAILY',
            'OVERNIGHT',
            'TIERED'
        ) NOT NULL,

                                      base_price DECIMAL(10,2) DEFAULT 0,

                                      hourly_rate DECIMAL(10,2) DEFAULT 0,

                                      max_hours INT DEFAULT 24,

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

                                      effective_from DATETIME,

                                      effective_to DATETIME,

                                      status ENUM(
            'ACTIVE',
            'INACTIVE'
        ) DEFAULT 'ACTIVE',

                                      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                                      CONSTRAINT fk_policy_vehicle_type
                                          FOREIGN KEY (vehicle_type_id)
                                              REFERENCES vehicle_types(vehicle_type_id)
    );

    -- =========================================================
    -- INCIDENTS
    -- =========================================================

    CREATE TABLE incidents (
                               incident_id CHAR(36) PRIMARY KEY DEFAULT (UUID()),

                               session_id CHAR(36) NOT NULL,

                               incident_type ENUM(
            'LOST_TICKET',
            'WRONG_VEHICLE',
            'OVERTIME',
            'UNPAID',
            'OTHER'
        ) NOT NULL,

                               description VARCHAR(500),

                               status ENUM(
            'OPEN',
            'PROCESSING',
            'RESOLVED'
        ) DEFAULT 'OPEN',

                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_incidents_session
                                   FOREIGN KEY (session_id)
                                       REFERENCES parking_sessions(session_id)
                                       ON DELETE CASCADE
    );

    -- =========================================================
    -- SAMPLE USERS
    -- =========================================================

    INSERT INTO users (
        user_id,
        username,
        password_hash,
        full_name,
        role,
        status
    )
    VALUES
        (
            UUID(),
            'admin',
            '$2a$10$examplehashedpassword',
            'System Administrator',
            'ROLE_ADMIN',
            'ACTIVE'
        ),
        (
            UUID(),
            'manager1',
            '$2a$10$examplehashedpassword',
            'Parking Manager',
            'ROLE_MANAGER',
            'ACTIVE'
        ),
        (
            UUID(),
            'staff1',
            '$2a$10$examplehashedpassword',
            'Parking Staff',
            'ROLE_STAFF',
            'ACTIVE'
        ),
        (
            UUID(),
            'driver1',
            '$2a$10$examplehashedpassword',
            'Driver User',
            'ROLE_DRIVER',
            'ACTIVE'
        );

    -- =========================================================
    -- SAMPLE VEHICLE TYPES
    -- =========================================================

    INSERT INTO vehicle_types (
        vehicle_type_id,
        type_name,
        size_category,
        description
    )
    VALUES
        (
            '33333333-3333-3333-3333-333333333331',
            'Motorbike',
            'SMALL',
            'Standard motorbike'
        ),
        (
            '33333333-3333-3333-3333-333333333332',
            'Car',
            'MEDIUM',
            '4-seat or 7-seat car'
        );

    -- =========================================================
    -- SAMPLE BUILDING
    -- =========================================================

    INSERT INTO buildings (
        building_name,
        address,
        total_floors,
        operating_start_time,
        operating_end_time,
        contact_number
    )
    VALUES
        (
            'Main Parking Building',
            'District 1, Ho Chi Minh City',
            5,
            '06:00:00',
            '23:00:00',
            '0909000000'
        );

    -- =========================================================
    -- SAMPLE BUILDING STAFF (manager assigns staff to building)
    -- =========================================================

    INSERT INTO building_staff (assignment_id, building_id, user_id)
    SELECT UUID(), b.building_id, u.user_id
    FROM users u
    JOIN buildings b ON b.building_name = 'Main Parking Building'
    WHERE u.username = 'staff1'
      AND u.role = 'ROLE_STAFF';

    -- =========================================================
    -- FINISHED
    -- =========================================================