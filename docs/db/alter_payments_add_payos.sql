-- Add PAYOS (and ensure VNPAY/PENDING) for PayOS payment testing.
-- Run: mysql -u root -p parking_db < docs/db/alter_payments_add_payos.sql

USE parking_db;

ALTER TABLE payments
    MODIFY COLUMN payment_method ENUM('CASH', 'BANKING', 'MOMO', 'VNPAY', 'PAYOS') NOT NULL;

ALTER TABLE payments
    MODIFY COLUMN payment_status ENUM('PENDING', 'PAID', 'CONFIRMED', 'SUCCESS', 'FAILED') DEFAULT 'PENDING';
