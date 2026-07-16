-- Rollback performance indexes (pair with add_performance_indexes.sql)
-- Use when indexes cause issues or you need to undo the migration.
-- Does NOT delete or modify table data — only removes index structures.
-- Run each DROP INDEX statement separately (one at a time).

-- Availability / occupancy aggregations
DROP INDEX idx_parking_slots_zone_status ON parking_slots;
DROP INDEX idx_parking_slots_status ON parking_slots;
DROP INDEX idx_zones_floor ON zones;
DROP INDEX idx_floors_building_vehicle ON floors;
DROP INDEX idx_floors_vehicle ON floors;

-- Reservations
DROP INDEX idx_res_status_start ON reservations;
DROP INDEX idx_res_slot_status ON reservations;
DROP INDEX idx_res_user_created ON reservations;
DROP INDEX idx_res_vehicle_status ON reservations;

-- Parking sessions
DROP INDEX idx_ps_vehicle_status_checkin ON parking_sessions;
DROP INDEX idx_ps_vehicle_checkin ON parking_sessions;
DROP INDEX idx_ps_slot_status ON parking_sessions;
DROP INDEX idx_ps_reservation_checkin ON parking_sessions;
DROP INDEX idx_ps_status_checkin ON parking_sessions;

-- Payments
DROP INDEX idx_pay_status_created ON payments;
DROP INDEX idx_pay_status_time ON payments;
DROP INDEX idx_pay_session_created ON payments;

-- Vehicles / users
DROP INDEX idx_vehicles_plate ON vehicles;
DROP INDEX idx_vehicles_status_created ON vehicles;
DROP INDEX idx_users_role ON users;

-- Incidents
DROP INDEX idx_incidents_status_created ON incidents;
