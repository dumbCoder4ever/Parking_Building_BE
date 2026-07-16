package fpt.swp391.parkingmanagement.repository;

/**
 * Aggregate floor metrics per building — used to avoid N×K count queries
 * when listing manager building summaries.
 */
public class BuildingFloorStats {

    private final String buildingId;
    private final Long floorCount;
    private final Long maxCapacity;
    private final Long currentOccupancy;

    public BuildingFloorStats(String buildingId, Long floorCount, Long maxCapacity, Long currentOccupancy) {
        this.buildingId = buildingId;
        this.floorCount = floorCount;
        this.maxCapacity = maxCapacity;
        this.currentOccupancy = currentOccupancy;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public long getFloorCount() {
        return floorCount != null ? floorCount : 0L;
    }

    public int getMaxCapacity() {
        return maxCapacity != null ? maxCapacity.intValue() : 0;
    }

    public int getCurrentOccupancy() {
        return currentOccupancy != null ? currentOccupancy.intValue() : 0;
    }
}
