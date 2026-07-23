package fpt.swp391.parkingmanagement.repository;

/**
 * Lightweight per-zone totals for GET /buildings/{id}/floors.
 * Avoids the full {@link ZoneSlotCount} projection.
 */
public class ZoneAvailabilityCount {

    private final String zoneId;
    private final Long totalSlots;
    private final Long availableSlots;

    public ZoneAvailabilityCount(String zoneId, Long totalSlots, Long availableSlots) {
        this.zoneId = zoneId;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Long getTotalSlots() {
        return totalSlots != null ? totalSlots : 0L;
    }

    public Long getAvailableSlots() {
        return availableSlots != null ? availableSlots : 0L;
    }
}
