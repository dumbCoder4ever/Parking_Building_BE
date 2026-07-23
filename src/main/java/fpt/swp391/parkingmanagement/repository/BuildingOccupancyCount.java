package fpt.swp391.parkingmanagement.repository;

/**
 * Building-level slot occupancy for admin dashboard (no per-zone GROUP BY).
 */
public class BuildingOccupancyCount {

    private final String buildingId;
    private final String buildingName;
    private final Long totalSlots;
    private final Long availableSlots;
    private final Long reservedSlots;
    private final Long occupiedSlots;
    private final Long pendingExitSlots;

    public BuildingOccupancyCount(
            String buildingId,
            String buildingName,
            Long totalSlots,
            Long availableSlots,
            Long reservedSlots,
            Long occupiedSlots,
            Long pendingExitSlots) {
        this.buildingId = buildingId;
        this.buildingName = buildingName;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.reservedSlots = reservedSlots;
        this.occupiedSlots = occupiedSlots;
        this.pendingExitSlots = pendingExitSlots;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public long getTotalSlots() {
        return nz(totalSlots);
    }

    public long getAvailableSlots() {
        return nz(availableSlots);
    }

    public long getReservedSlots() {
        return nz(reservedSlots);
    }

    public long getOccupiedSlots() {
        return nz(occupiedSlots);
    }

    public long getPendingExitSlots() {
        return nz(pendingExitSlots);
    }
}
