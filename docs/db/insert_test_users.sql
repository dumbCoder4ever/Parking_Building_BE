-- Insert / upsert test users into existing parking_db
-- Safe to run: will insert new users or update existing ones by username (ON DUPLICATE KEY UPDATE)
-- Credentials (for testing):
-- admin / admin@example.com  -> password: 123
-- manager1 / manager1@example.com  -> password: 123
-- staff1 / staff1@example.com  -> password: 123
-- driver1 / driver1@example.com  -> password: 1

INSERT INTO users (user_id, username, password_hash, full_name, email, role, status, is_active)
VALUES
  (UUID(), 'admin',  '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'System Administrator', 'admin@example.com',  'ROLE_ADMIN',   'ACTIVE', TRUE)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash), full_name = VALUES(full_name), email = VALUES(email), role = VALUES(role), status = VALUES(status), is_active = VALUES(is_active);

INSERT INTO users (user_id, username, password_hash, full_name, email, role, status, is_active)
VALUES
  (UUID(), 'manager1',  '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Parking Manager',      'manager1@example.com', 'ROLE_MANAGER', 'ACTIVE', TRUE)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash), full_name = VALUES(full_name), email = VALUES(email), role = VALUES(role), status = VALUES(status), is_active = VALUES(is_active);

INSERT INTO users (user_id, username, password_hash, full_name, email, role, status, is_active)
VALUES
  (UUID(), 'staff1',    '$2a$10$kCFmLRxZpQFr2iZ8EtNJw.VAOTAf01uxV8HPsUCKZf5uxCe1sZLea', 'Parking Staff',        'staff1@example.com',   'ROLE_STAFF',   'ACTIVE', TRUE)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash), full_name = VALUES(full_name), email = VALUES(email), role = VALUES(role), status = VALUES(status), is_active = VALUES(is_active);

INSERT INTO users (user_id, username, password_hash, full_name, email, role, status, is_active)
VALUES
  (UUID(), 'driver1',   '$2a$10$3R/XmppgsdJqbVcjBda5m.qNC8S.4u8cSb9n9yYoTuCg8SFWn/r0y', 'Driver User',         'driver1@example.com',  'ROLE_DRIVER',  'ACTIVE', TRUE)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash), full_name = VALUES(full_name), email = VALUES(email), role = VALUES(role), status = VALUES(status), is_active = VALUES(is_active);

-- To run:
-- 1) save this file as insert_test_users.sql
-- 2) run:
--    mysql -u <db_user> -p parking_db < insert_test_users.sql
-- After running, test login via POST /api/auth/login with JSON { "gmail": "admin@example.com", "password": "123" }
