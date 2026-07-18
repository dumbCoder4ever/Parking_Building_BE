package fpt.swp391.parkingmanagement.repository;

/**
 * Lightweight projection for slot counts per zone — used by BuildingService.listFloorsOfBuilding
 * to replace N x countByZoneZoneId queries with a single aggregate query.
 *
 * Returns: zoneId, totalSlots, availableSlots in ONE query instead of 2 per zone.
 */
public class ZoneSlotCountLite {

    private final String zoneId;
    private final Long totalSlots;
    private final Long availableSlots;

    public ZoneSlotCountLite(String zoneId, Long totalSlots, Long availableSlots) {
        this.zoneId = zoneId;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
    }

    public String getZoneId() { return zoneId; }
    public Long getTotalSlots() { return totalSlots; }
    public Long getAvailableSlots() { return availableSlots; }
}
