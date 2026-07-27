package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.service.NotificationService;
import fpt.swp391.parkingmanagement.service.PeakHourService;
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
public class PeakHourNotificationJob {

    private final BuildingRepository buildingRepository;
    private final PeakHourService peakHourService;
    private final NotificationService notificationService;

    private final ConcurrentHashMap<String, Integer> lastNotifiedHour = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 600000) // every 10 minutes
    @Transactional(readOnly = true)
    public void notifyPeakHours() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        List<Building> buildings = buildingRepository.findByStatusIgnoreCaseOrderByBuildingNameAsc("ACTIVE");
        int notified = 0;
        for (Building building : buildings) {
            String buildingId = building.getBuildingId();
            Integer lastHour = lastNotifiedHour.get(buildingId);
            if (lastHour != null && lastHour == hour) {
                continue;
            }
            if (!peakHourService.isPeakHour(buildingId, now)) {
                continue;
            }
            Map<String, Object> payload = Map.of(
                    "buildingId", buildingId,
                    "buildingName", building.getBuildingName() != null ? building.getBuildingName() : "",
                    "hour", hour,
                    "message", "Building is currently in peak hours");
            notificationService.sendToStaffBuilding(buildingId, "PEAK_HOUR_ALERT", payload);
            notificationService.broadcastToAdmins("PEAK_HOUR_ALERT", payload);
            lastNotifiedHour.put(buildingId, hour);
            notified++;
        }
        if (notified > 0) {
            log.info("PeakHourNotificationJob: alerted {} buildings at hour {}", notified, hour);
        }
    }
}
