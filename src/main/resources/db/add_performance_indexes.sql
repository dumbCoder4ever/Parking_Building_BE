-- Performance indexes for Parking Building BE
-- Apply on MySQL 5.7+ after backup.
-- Run each CREATE INDEX statement separately (one at a time).
-- If an index already exists, MySQL will return a duplicate error — skip and continue.

-- Availability / occupancy aggregations
CREATE INDEX idx_parking_slots_zone_status ON parking_slots(zone_id, slot_status);
CREATE INDEX idx_parking_slots_status ON parking_slots(slot_status);
CREATE INDEX idx_zones_floor ON zones(floor_id);
CREATE INDEX idx_floors_building_vehicle ON floors(building_id, vehicle_type_id);
CREATE INDEX idx_floors_vehicle ON floors(vehicle_type_id);

-- Reservations (list, expire job, overlap checks)
CREATE INDEX idx_res_status_start ON reservations(reservation_status, reservation_start);
CREATE INDEX idx_res_slot_status ON reservations(slot_id, reservation_status);
CREATE INDEX idx_res_user_created ON reservations(user_id, created_at);
CREATE INDEX idx_res_vehicle_status ON reservations(vehicle_id, reservation_status);

-- Parking sessions (manager vehicles, driver history, dashboard)
CREATE INDEX idx_ps_vehicle_status_checkin ON parking_sessions(vehicle_id, session_status, checkin_time);
CREATE INDEX idx_ps_vehicle_checkin ON parking_sessions(vehicle_id, checkin_time);
CREATE INDEX idx_ps_slot_status ON parking_sessions(slot_id, session_status);
CREATE INDEX idx_ps_reservation_checkin ON parking_sessions(reservation_id, checkin_time);
CREATE INDEX idx_ps_status_checkin ON parking_sessions(session_status, checkin_time);

-- Payments (staff list + revenue)
CREATE INDEX idx_pay_status_created ON payments(payment_status, created_at);
CREATE INDEX idx_pay_status_time ON payments(payment_status, payment_time);
CREATE INDEX idx_pay_session_created ON payments(session_id, created_at);

-- Vehicles / users
CREATE INDEX idx_vehicles_plate ON vehicles(plate_number);
CREATE INDEX idx_tickets_reservation ON tickets(reservation_id);
CREATE INDEX idx_vehicles_status_created ON vehicles(status, created_at);
CREATE INDEX idx_users_role ON users(role);

-- Incidents
CREATE INDEX idx_incidents_status_created ON incidents(status, created_at);
