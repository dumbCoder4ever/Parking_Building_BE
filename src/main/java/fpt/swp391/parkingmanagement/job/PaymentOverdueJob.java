package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAYMENT_OVERDUE job disabled (Notification module removed).
 * Kept as a placeholder that periodically scans overdue sessions for
 * other downstream consumers (audit, dashboards, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOverdueJob {

    private static final int OVERDUE_MINUTES = 30;

    private final ParkingSessionRepository parkingSessionRepository;

    @Scheduled(fixedRate = 600000) // every 10 minutes
    @Transactional(readOnly = true)
    public void notifyOverduePayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(OVERDUE_MINUTES);
        List<ParkingSession> sessions = parkingSessionRepository.findPendingPaymentSessionsBefore(cutoff);
        if (sessions.isEmpty()) {
            return;
        }
        log.info("PaymentOverdueJob: found {} overdue sessions (notifications disabled)", sessions.size());
    }
}