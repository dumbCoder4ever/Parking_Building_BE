package fpt.swp391.parkingmanagement.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fpt.swp391.parkingmanagement.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCancelReservationJob {

    private final ReservationService reservationService;

    @Scheduled(fixedRate = 60000)
    public void autoExpireReservations() {
        log.info("Running auto-expire reservations job...");
        try {
            int expiredCount = reservationService.autoExpireReservations();
            if (expiredCount > 0) {
                log.info("Auto-expired {} reservations", expiredCount);
            } else {
                log.debug("No expired reservations found");
            }
        } catch (Exception e) {
            log.error("Error while auto-expiring reservations", e);
        }
    }
}
