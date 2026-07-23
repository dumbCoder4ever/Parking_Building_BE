package fpt.swp391.parkingmanagement.repository;

/**
 * Class-based projection aggregating slot counts per zone (used by dashboard & availability endpoints).
 * Returns counts per zone in a single query, eliminating N+1 round-trips.
 *
 * Must be a concrete class with a full constructor because the JPA query uses
 * {@code SELECT new fpt.swp391.parkingmanagement.repository.ZoneSlotCount(...)}.
 */
public class ZoneSlotCount {

    private final String zoneId;
    private final String zoneName;
    private final String zoneStatus;
    private final String floorId;
    private final String floorName;
    private final Integer floorLevel;
    private final String floorStatus;
    private final String vehicleTypeId;
    private final String vehicleTypeName;
    private final String buildingId;
    private final String buildingName;
    private final String buildingStatus;
    private final Long totalSlots;
    private final Long availableSlots;
    private final Long reservedSlots;
    private final Long occupiedSlots;
    private final Long pendingExitSlots;

    public ZoneSlotCount(String zoneId, String zoneName, String zoneStatus,
                         String floorId, String floorName, Integer floorLevel, String floorStatus,
                         String vehicleTypeId, String vehicleTypeName,
                         String buildingId, String buildingName, String buildingStatus,
                         Long totalSlots, Long availableSlots, Long reservedSlots, Long occupiedSlots,
                         Long pendingExitSlots) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.zoneStatus = zoneStatus;
        this.floorId = floorId;
        this.floorName = floorName;
        this.floorLevel = floorLevel;
        this.floorStatus = floorStatus;
        this.vehicleTypeId = vehicleTypeId;
        this.vehicleTypeName = vehicleTypeName;
        this.buildingId = buildingId;
        this.buildingName = buildingName;
        this.buildingStatus = buildingStatus;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.reservedSlots = reservedSlots;
        this.occupiedSlots = occupiedSlots;
        this.pendingExitSlots = pendingExitSlots;
    }

    public String getZoneId() { return zoneId; }
    public String getZoneName() { return zoneName; }
    public String getZoneStatus() { return zoneStatus; }
    public String getFloorId() { return floorId; }
    public String getFloorName() { return floorName; }
    public Integer getFloorLevel() { return floorLevel; }
    public String getFloorStatus() { return floorStatus; }
    public String getVehicleTypeId() { return vehicleTypeId; }
    public String getVehicleTypeName() { return vehicleTypeName; }
    public String getBuildingId() { return buildingId; }
    public String getBuildingName() { return buildingName; }
    public String getBuildingStatus() { return buildingStatus; }
    public Long getTotalSlots() { return totalSlots; }
    public Long getAvailableSlots() { return availableSlots; }
    public Long getReservedSlots() { return reservedSlots; }
    public Long getOccupiedSlots() { return occupiedSlots; }
    public Long getPendingExitSlots() { return pendingExitSlots; }
}