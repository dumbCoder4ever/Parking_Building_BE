-- Add guest fields to parking_sessions table for walk-in (guest) parking flow
ALTER TABLE parking_sessions
    ADD COLUMN guest_name VARCHAR(100) NULL AFTER note,
    ADD COLUMN guest_phone VARCHAR(20) NULL AFTER guest_name;
