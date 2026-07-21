package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.BuildingRule;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.repository.BuildingRuleRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.service.AuditLogService;
import fpt.swp391.parkingmanagement.service.BuildingRuleService;
import fpt.swp391.parkingmanagement.service.NotificationService;
import fpt.swp391.parkingmanagement.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleOverstayJob {

    private final ParkingSessionRepository parkingSessionRepository;
    private final BuildingRuleRepository buildingRuleRepository;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    private final ConcurrentHashMap<String, LocalDateTime> lastNotified = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 300000) // every 5 minutes
    @Transactional(readOnly = true)
    public void checkOverstay() {
        if (!systemConfigService.getBoolean(SystemConfigService.OVERSTAY_NOTIFY_ENABLED, true)) {
            return;
        }
        int defaultMaxHours = systemConfigService.getInt(SystemConfigService.MAX_PARKING_HOURS, 24);
        LocalDateTime now = LocalDateTime.now();
        // Prefetch sessions older than 1 hour to reduce load; filter by max hours below
        LocalDateTime prefetchCutoff = now.minusHours(1);
        List<ParkingSession> sessions = parkingSessionRepository.findActiveSessionsCheckedInBefore(prefetchCutoff);
        if (sessions.isEmpty()) {
            return;
        }

        int notified = 0;
        for (ParkingSession ps : sessions) {
            if (ps.getCheckinTime() == null) {
                continue;
            }
            Building building = resolveBuilding(ps);
            int maxHours = resolveMaxHours(building, defaultMaxHours);
            long parkedHours = Duration.between(ps.getCheckinTime(), now).toHours();
            if (parkedHours < maxHours) {
                continue;
            }
            LocalDateTime last = lastNotified.get(ps.getSessionId());
            if (last != null && last.isAfter(now.minusHours(1))) {
                continue;
            }

            String buildingId = building != null ? building.getBuildingId() : null;
            String plate = ps.getVehicle() != null ? ps.getVehicle().getPlateNumber() : null;
            Map<String, Object> payload = Map.of(
                    "sessionId", ps.getSessionId(),
                    "buildingId", buildingId != null ? buildingId : "",
                    "plateNumber", plate != null ? plate : "",
                    "checkinTime", ps.getCheckinTime().toString(),
                    "parkedHours", parkedHours,
                    "maxParkingHours", maxHours,
                    "message", "Xe đã vượt thời gian đỗ tối đa (" + maxHours + "h)");

            if (buildingId != null) {
                notificationService.sendToStaffBuilding(buildingId, "VEHICLE_OVERSTAY", payload);
            }
            notificationService.broadcastToAdmins("VEHICLE_OVERSTAY", payload);
            notifyDriver(ps, payload);

            auditLogService.recordSystem(
                    "VEHICLE_OVERSTAY",
                    "PARKING_SESSION",
                    ps.getSessionId(),
                    buildingId,
                    null,
                    parkedHours + "h",
                    "Overstay alert: parked " + parkedHours + "h (max " + maxHours + "h)");

            lastNotified.put(ps.getSessionId(), now);
            notified++;
        }
        if (notified > 0) {
            log.info("VehicleOverstayJob: notified {} sessions", notified);
        }
        pruneNotifiedCache(now);
    }

    private int resolveMaxHours(Building building, int defaultMax) {
        if (building == null) {
            return defaultMax;
        }
        List<BuildingRule> rules = buildingRuleRepository.findActiveByBuildingId(building.getBuildingId());
        for (BuildingRule rule : rules) {
            if (BuildingRuleService.CODE_MAX_PARKING_HOURS.equalsIgnoreCase(rule.getRuleCode())
                    && rule.getRuleValue() != null) {
                try {
                    int v = Integer.parseInt(rule.getRuleValue().trim());
                    if (v > 0) {
                        return v;
                    }
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
        }
        return defaultMax;
    }

    private Building resolveBuilding(ParkingSession ps) {
        if (ps.getSlot() == null || ps.getSlot().getZone() == null
                || ps.getSlot().getZone().getFloor() == null) {
            return null;
        }
        return ps.getSlot().getZone().getFloor().getBuilding();
    }

    private void notifyDriver(ParkingSession ps, Map<String, Object> payload) {
        if (ps.getReservation() != null && ps.getReservation().getUser() != null
                && ps.getReservation().getUser().getUsername() != null) {
            notificationService.sendToUser(
                    ps.getReservation().getUser().getUsername(), "VEHICLE_OVERSTAY", payload);
        }
    }

    private void pruneNotifiedCache(LocalDateTime now) {
        lastNotified.entrySet().removeIf(e -> e.getValue().isBefore(now.minusDays(1)));
    }
}
