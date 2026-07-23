package fpt.swp391.parkingmanagement.job;

import fpt.swp391.parkingmanagement.entity.Building;
import fpt.swp391.parkingmanagement.entity.Floor;
import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.repository.BuildingRepository;
import fpt.swp391.parkingmanagement.repository.FloorRepository;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import fpt.swp391.parkingmanagement.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceNotificationJob {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final NotificationService notificationService;

    private final ConcurrentHashMap<String, LocalDateTime> lastNotified = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional(readOnly = true)
    public void remindMaintenance() {
        LocalDateTime now = LocalDateTime.now();
        List<Building> buildings = buildingRepository.findByStatusIgnoreCaseOrderByBuildingNameAsc("MAINTENANCE");
        List<Floor> floors = floorRepository.findByStatusIgnoreCase("MAINTENANCE");
        List<Zone> zones = zoneRepository.findByStatusIgnoreCase("MAINTENANCE");
        List<ParkingSlot> slots = parkingSlotRepository.findBySlotStatusIgnoreCaseFetchingBuilding("MAINTENANCE");

        if (buildings.isEmpty() && floors.isEmpty() && zones.isEmpty() && slots.isEmpty()) {
            return;
        }

        Map<String, Integer> buildingCounts = new HashMap<>();
        for (Building b : buildings) {
            buildingCounts.merge(b.getBuildingId(), 1, Integer::sum);
        }
        for (Floor f : floors) {
            if (f.getBuilding() != null) {
                buildingCounts.merge(f.getBuilding().getBuildingId(), 1, Integer::sum);
            }
        }
        for (Zone z : zones) {
            if (z.getFloor() != null && z.getFloor().getBuilding() != null) {
                buildingCounts.merge(z.getFloor().getBuilding().getBuildingId(), 1, Integer::sum);
            }
        }
        for (ParkingSlot s : slots) {
            if (s.getZone() != null && s.getZone().getFloor() != null
                    && s.getZone().getFloor().getBuilding() != null) {
                buildingCounts.merge(s.getZone().getFloor().getBuilding().getBuildingId(), 1, Integer::sum);
            }
        }

        Map<String, Object> summary = Map.of(
                "buildings", buildings.size(),
                "floors", floors.size(),
                "zones", zones.size(),
                "slots", slots.size(),
                "message", "Có hạng mục đang MAINTENANCE cần theo dõi");

        // Global admin broadcast at most once per 6 hours
        LocalDateTime lastGlobal = lastNotified.get("GLOBAL");
        if (lastGlobal == null || lastGlobal.isBefore(now.minusHours(6))) {
            notificationService.broadcastToAdmins("MAINTENANCE_REMINDER", summary);
            lastNotified.put("GLOBAL", now);
        }

        for (Map.Entry<String, Integer> e : buildingCounts.entrySet()) {
            String buildingId = e.getKey();
            LocalDateTime last = lastNotified.get(buildingId);
            if (last != null && last.isAfter(now.minusHours(6))) {
                continue;
            }
            notificationService.sendToStaffBuilding(buildingId, "MAINTENANCE_REMINDER", Map.of(
                    "buildingId", buildingId,
                    "maintenanceItems", e.getValue(),
                    "message", "Building có " + e.getValue() + " hạng mục đang bảo trì"));
            lastNotified.put(buildingId, now);
        }

        log.debug("MaintenanceNotificationJob: buildings={}, floors={}, zones={}, slots={}",
                buildings.size(), floors.size(), zones.size(), slots.size());
    }
}
