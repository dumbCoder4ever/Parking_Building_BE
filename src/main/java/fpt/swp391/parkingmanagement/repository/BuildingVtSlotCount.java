package fpt.swp391.parkingmanagement.repository;

/**
 * Lightweight building × vehicle-type slot totals for GET /buildings/available.
 * Avoids per-zone GROUP BY used by {@link ZoneSlotCount}.
 */
public class BuildingVtSlotCount {

    private final String buildingId;
    private final String vehicleTypeId;
    private final String vehicleTypeName;
    private final Long totalSlots;
    private final Long availableSlots;

    public BuildingVtSlotCount(String buildingId, String vehicleTypeId, String vehicleTypeName,
                               Long totalSlots, Long availableSlots) {
        this.buildingId = buildingId;
        this.vehicleTypeId = vehicleTypeId;
        this.vehicleTypeName = vehicleTypeName;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
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

    public Long getTotalSlots() {
        return totalSlots != null ? totalSlots : 0L;
    }

    public Long getAvailableSlots() {
        return availableSlots != null ? availableSlots : 0L;
    }
}
