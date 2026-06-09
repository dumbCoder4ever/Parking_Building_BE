USE parking_db;

-- =========================================================
-- AUTO CANCEL RESERVATION
-- =========================================================

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS CancelExpiredReservations()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            RESIGNAL;
        END;

    START TRANSACTION;

    -- Update reservation status to CANCELLED and slot status to AVAILABLE
    -- for reservations where grace period has passed
    UPDATE reservations r
        INNER JOIN parking_slots ps ON r.slot_id = ps.slot_id
    SET
        r.reservation_status = 'CANCELLED',
        r.updated_at = CURRENT_TIMESTAMP,
        ps.slot_status = 'AVAILABLE',
        ps.updated_at = CURRENT_TIMESTAMP
    WHERE r.reservation_status = 'PENDING'
      AND ps.slot_status = 'RESERVED'
      -- AND r.reservation_end < NOW()
      AND DATE_ADD(r.reservation_end, INTERVAL r.grace_period_minutes MINUTE) <= NOW();

    COMMIT;
END$$

DELIMITER ;

-- =========================================================
-- SCHEDULED EVENT: AutoCancelReservationsEvent
-- =========================================================
-- Runs every 5 minutes to check and cancel expired reservations
-- Adjust the INTERVAL if needed (e.g., EVERY 1 MINUTE for more frequent checks)

CREATE EVENT IF NOT EXISTS AutoCancelReservationsEvent
    ON SCHEDULE EVERY 1 MINUTE
        STARTS CURRENT_TIMESTAMP
    ENABLE
    DO
    CALL CancelExpiredReservations();

