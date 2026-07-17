package fpt.swp391.parkingmanagement.repository;

/**
 * Flat row for GET /buildings/{id}/floors — one SQL round-trip for floors + zones + counts.
 */
public class FloorZoneAvailabilityRow {

    private final String floorId;
    private final Integer floorLevel;
    private final String floorStatus;
    private final String buildingId;
    private final String vehicleTypeId;
    private final String vehicleTypeName;
    private final String zoneId;
    private final String zoneName;
    private final String zoneStatus;
    private final Long totalSlots;
    private final Long availableSlots;

    public FloorZoneAvailabilityRow(
            String floorId,
            Integer floorLevel,
            String floorStatus,
            String buildingId,
            String vehicleTypeId,
            String vehicleTypeName,
            String zoneId,
            String zoneName,
            String zoneStatus,
            Long totalSlots,
            Long availableSlots) {
        this.floorId = floorId;
        this.floorLevel = floorLevel;
        this.floorStatus = floorStatus;
        this.buildingId = buildingId;
        this.vehicleTypeId = vehicleTypeId;
        this.vehicleTypeName = vehicleTypeName;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.zoneStatus = zoneStatus;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
    }

    public String getFloorId() {
        return floorId;
    }

    public Integer getFloorLevel() {
        return floorLevel;
    }

    public String getFloorStatus() {
        return floorStatus;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public String getVehicleTypeId() {
        return vehicleTypeId;
    }

    public String getVehicleTypeName() {
        return vehicleTypeName;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public String getZoneStatus() {
        return zoneStatus;
    }

    public long getTotalSlots() {
        return totalSlots != null ? totalSlots : 0L;
    }

    public long getAvailableSlots() {
        return availableSlots != null ? availableSlots : 0L;
    }
}
