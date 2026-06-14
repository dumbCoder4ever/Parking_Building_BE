-- Tắt safe mode
SET SQL_SAFE_UPDATES = 0;

-- Fix NULL và empty string trước
UPDATE reservations SET reservation_status = 'APPROVED' WHERE reservation_status IS NULL OR reservation_status = '';

-- Fix mọi giá trị không hợp lệ
UPDATE reservations SET reservation_status = 'APPROVED' WHERE reservation_status NOT IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'NO_SHOW', 'CHECKED_IN', 'PENDING_PAYMENT');

-- Verify đã hết lỗi
SELECT reservation_id, reservation_code, reservation_status FROM reservations WHERE reservation_status IS NULL OR reservation_status = '';

-- Bật lại safe mode
SET SQL_SAFE_UPDATES = 1;

-- Giờ alter table
ALTER TABLE reservations
MODIFY COLUMN reservation_status ENUM(
    'PENDING',
    'APPROVED',
    'REJECTED',
    'CANCELLED',
    'COMPLETED',
    'NO_SHOW',
    'CHECKED_IN',
    'PENDING_PAYMENT'
) NOT NULL DEFAULT 'PENDING';
