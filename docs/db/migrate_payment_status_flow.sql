-- =============================================================================
-- Migration: Payment status flow (PENDING -> PAID -> CONFIRMED)
-- Project   : Parking_Building_BE
-- Date      : 2026-06-13
-- Purpose   : Sync MySQL schema with new payment flow used by backend + FE
--
-- Run (Windows CMD):
--   mysql -u root -p parking_db < docs/db/migrate_payment_status_flow.sql
--
-- Run (MySQL client):
--   USE parking_db;
--   SOURCE docs/db/migrate_payment_status_flow.sql;
--
-- Safe to re-run: uses MODIFY COLUMN (idempotent on same target definition)
-- =============================================================================

USE parking_db;

-- -----------------------------------------------------------------------------
-- 1) payments.payment_method
--    Ensure electronic payment methods exist (PayOS / VNPay)
-- -----------------------------------------------------------------------------
ALTER TABLE payments
    MODIFY COLUMN payment_method ENUM(
        'CASH',
        'BANKING',
        'MOMO',
        'VNPAY',
        'PAYOS'
    ) NOT NULL;

-- -----------------------------------------------------------------------------
-- 2) payments.payment_status
--    Old schema : SUCCESS, FAILED, PENDING
--    New schema : PENDING, PAID, CONFIRMED, SUCCESS (legacy), FAILED
--
--    Flow:
--      PENDING   -> waiting for driver payment
--      PAID      -> gateway paid, waiting for staff confirm
--      CONFIRMED -> staff confirmed, ready for checkout
--      SUCCESS   -> kept for old records / backward compatibility
--      FAILED    -> payment failed or staff rejected
-- -----------------------------------------------------------------------------
ALTER TABLE payments
    MODIFY COLUMN payment_status ENUM(
        'PENDING',
        'PAID',
        'CONFIRMED',
        'SUCCESS',
        'FAILED'
    ) DEFAULT 'PENDING';

-- -----------------------------------------------------------------------------
-- 3) parking_sessions.session_status
--    Add checkout flow states used when driver pays electronically
-- -----------------------------------------------------------------------------
ALTER TABLE parking_sessions
    MODIFY COLUMN session_status ENUM(
        'ACTIVE',
        'PENDING_PAYMENT',
        'PENDING_EXIT',
        'COMPLETED',
        'CANCELLED'
    ) DEFAULT 'ACTIVE';

-- -----------------------------------------------------------------------------
-- 4) parking_sessions.payment_status
--    Session-level payment flag (different from payments.payment_status)
-- -----------------------------------------------------------------------------
ALTER TABLE parking_sessions
    MODIFY COLUMN payment_status ENUM(
        'UNPAID',
        'PAID',
        'FAILED'
    ) DEFAULT 'UNPAID';

-- -----------------------------------------------------------------------------
-- 5) reservations.reservation_status
--    Backend may set PENDING_PAYMENT during electronic checkout flow
-- -----------------------------------------------------------------------------
ALTER TABLE reservations
    MODIFY COLUMN reservation_status ENUM(
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELLED',
        'EXPIRED',
        'COMPLETED',
        'PENDING_PAYMENT'
    ) DEFAULT 'PENDING';

-- -----------------------------------------------------------------------------
-- 6) parking_slots.slot_status
--    Backend sets PENDING_EXIT after driver paid, before staff releases slot
-- -----------------------------------------------------------------------------
ALTER TABLE parking_slots
    MODIFY COLUMN slot_status ENUM(
        'AVAILABLE',
        'RESERVED',
        'OCCUPIED',
        'MAINTENANCE',
        'PENDING_EXIT'
    ) DEFAULT 'AVAILABLE';

-- -----------------------------------------------------------------------------
-- Verification (run manually after migration)
-- -----------------------------------------------------------------------------
-- SHOW COLUMNS FROM payments LIKE 'payment_status';
-- SHOW COLUMNS FROM payments LIKE 'payment_method';
-- SHOW COLUMNS FROM parking_sessions LIKE 'session_status';
-- SHOW COLUMNS FROM parking_sessions LIKE 'payment_status';
-- SHOW COLUMNS FROM reservations LIKE 'reservation_status';
-- SHOW COLUMNS FROM parking_slots LIKE 'slot_status';
