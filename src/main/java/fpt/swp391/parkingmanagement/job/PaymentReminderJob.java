package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.service.AuditLogService;
import fpt.swp391.parkingmanagement.service.NotificationService;
import fpt.swp391.parkingmanagement.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReminderJob {

    private final ParkingSessionRepository parkingSessionRepository;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    private final ConcurrentHashMap<String, LocalDateTime> lastNotified = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 300000) // every 5 minutes
    @Transactional(readOnly = true)
    public void remindUnpaid() {
        int minutes = systemConfigService.getInt(SystemConfigService.PAYMENT_REMINDER_MINUTES, 15);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(minutes);
        List<ParkingSession> sessions = parkingSessionRepository.findPendingPaymentSessionsBefore(cutoff);
        if (sessions.isEmpty()) {
            return;
        }

        int notified = 0;
        for (ParkingSession ps : sessions) {
            LocalDateTime last = lastNotified.get(ps.getSessionId());
            if (last != null && last.isAfter(now.minusMinutes(Math.max(minutes, 15)))) {
                continue;
            }

            String buildingId = resolveBuildingId(ps);
            String plate = ps.getVehicle() != null ? ps.getVehicle().getPlateNumber() : "";
            String ticketCode = ps.getTicket() != null ? ps.getTicket().getTicketCode() : "";
            Map<String, Object> payload = Map.of(
                    "sessionId", ps.getSessionId(),
                    "buildingId", buildingId != null ? buildingId : "",
                    "plateNumber", plate != null ? plate : "",
                    "ticketCode", ticketCode != null ? ticketCode : "",
                    "checkinTime", ps.getCheckinTime() != null ? ps.getCheckinTime().toString() : "",
                    "message", "Payment reminder: session not PAID after " + minutes + " minutes");

            if (buildingId != null) {
                notificationService.sendToStaffBuilding(buildingId, "PAYMENT_REMINDER", payload);
            }
            if (ps.getReservation() != null && ps.getReservation().getUser() != null
                    && ps.getReservation().getUser().getUsername() != null) {
                notificationService.sendToUser(
                        ps.getReservation().getUser().getUsername(), "PAYMENT_REMINDER", payload);
            }

            auditLogService.recordSystem(
                    "PAYMENT_REMINDER",
                    "PARKING_SESSION",
                    ps.getSessionId(),
                    buildingId,
                    "PENDING_PAYMENT",
                    null,
                    "Payment reminder sent");

            lastNotified.put(ps.getSessionId(), now);
            notified++;
        }
        if (notified > 0) {
            log.info("PaymentReminderJob: notified {} sessions", notified);
        }
        lastNotified.entrySet().removeIf(e -> e.getValue().isBefore(now.minusDays(1)));
    }

    private String resolveBuildingId(ParkingSession ps) {
        if (ps.getSlot() == null || ps.getSlot().getZone() == null
                || ps.getSlot().getZone().getFloor() == null
                || ps.getSlot().getZone().getFloor().getBuilding() == null) {
            return null;
        }
        return ps.getSlot().getZone().getFloor().getBuilding().getBuildingId();
    }
}
