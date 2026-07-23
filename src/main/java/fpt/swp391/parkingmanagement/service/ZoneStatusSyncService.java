package fpt.swp391.parkingmanagement.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fpt.swp391.parkingmanagement.entity.ParkingSlot;
import fpt.swp391.parkingmanagement.entity.Zone;
import fpt.swp391.parkingmanagement.repository.ParkingSlotRepository;
import fpt.swp391.parkingmanagement.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-toggle zone status between ACTIVE and FULL based on AVAILABLE slots.
 * Does not override MAINTENANCE / INACTIVE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneStatusSyncService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ZoneRepository zoneRepository;
    private final CacheManager cacheManager;

    @Transactional
    public ParkingSlot updateSlotStatus(ParkingSlot slot, String newStatus) {
        slot.setSlotStatus(newStatus);
        ParkingSlot saved = parkingSlotRepository.save(slot);
        syncFromSlot(saved);
        return saved;
    }

    @Transactional
    public void syncFromSlot(ParkingSlot slot) {
        if (slot == null || slot.getZone() == null || slot.getZone().getZoneId() == null) {
            return;
        }
        syncZone(slot.getZone().getZoneId());
    }

    @Transactional
    public void syncFromSlots(Collection<ParkingSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        Set<String> zoneIds = new LinkedHashSet<>();
        for (ParkingSlot slot : slots) {
            if (slot != null && slot.getZone() != null && slot.getZone().getZoneId() != null) {
                zoneIds.add(slot.getZone().getZoneId());
            }
        }
        zoneIds.forEach(this::syncZone);
    }

    @Transactional
    public void syncZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return;
        }
        Zone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null) {
            return;
        }

        String current = zone.getStatus() == null ? "" : zone.getStatus().trim().toUpperCase();
        if ("MAINTENANCE".equals(current) || "INACTIVE".equals(current)) {
            return;
        }

        long totalSlots = parkingSlotRepository.countByZoneZoneId(zoneId);
        if (totalSlots <= 0) {
            return;
        }

        long available = parkingSlotRepository.countByZoneZoneIdAndSlotStatusIgnoreCase(zoneId, "AVAILABLE");
        String target = available <= 0 ? "FULL" : "ACTIVE";

        if (!Objects.equals(current, target)) {
            zone.setStatus(target);
            zoneRepository.save(zone);
            evictManagerCaches();
            log.debug("Zone {} status auto-synced: {} -> {} (available={}/{})",
                    zoneId, current, target, available, totalSlots);
        }
    }

    private void evictManagerCaches() {
        clearCache("managerZones");
        clearCache("managerBuildings");
    }

    private void clearCache(String name) {
        var cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }
}
