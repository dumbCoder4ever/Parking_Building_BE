package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.BuildingRule;
import fpt.swp391.parkingmanagement.entity.ParkingSession;
import fpt.swp391.parkingmanagement.repository.BuildingRuleRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSessionRepository;
import fpt.swp391.parkingmanagement.config.ParkingConfig;
import fpt.swp391.parkingmanagement.service.AuditLogService;
import fpt.swp391.parkingmanagement.service.BuildingRuleService;
import fpt.swp391.parkingmanagement.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * VehicleOverstayJob: scans active parking sessions and logs an audit
 * entry when a vehicle exceeds the configured max parking hours.
 *
 * Notification publishing has been removed (Notification module disabled).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleOverstayJob {

    private final ParkingSessionRepository parkingSessionRepository;
    private final BuildingRuleRepository buildingRuleRepository;
    private final ParkingConfig parkingConfig;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Scheduled(fixedRate = 300000) // every 5 minutes
    @Transactional(readOnly = true)
    public void checkOverstay() {
        if (!parkingConfig.isOverstayNotifyEnabled()) {
            return;
        }
        int defaultMaxHours = parkingConfig.getMaxParkingHours();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime prefetchCutoff = now.minusHours(1);
        List<ParkingSession> sessions = parkingSessionRepository.findActiveSessionsCheckedInBefore(prefetchCutoff);
        if (sessions.isEmpty()) {
            return;
        }

        int flagged = 0;
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

            String buildingId = building != null ? building.getBuildingId() : null;

            auditLogService.recordSystem(
                    "VEHICLE_OVERSTAY",
                    "PARKING_SESSION",
                    ps.getSessionId(),
                    buildingId,
                    null,
                    parkedHours + "h",
                    "Overstay alert: parked " + parkedHours + "h (max " + maxHours + "h)");
            flagged++;
        }
        if (flagged > 0) {
            log.info("VehicleOverstayJob: flagged {} sessions (notifications disabled)", flagged);
        }
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
}