-- Add PAID and CONFIRMED to payments.payment_status for new payment flow.
-- Run: mysql -u root -p parking_db < docs/db/alter_payments_add_paid_confirmed.sql

USE parking_db;

ALTER TABLE payments
    MODIFY COLUMN payment_status ENUM(
        'PENDING',
        'PAID',
        'CONFIRMED',
        'SUCCESS',
        'FAILED'
    ) DEFAULT 'PENDING';
