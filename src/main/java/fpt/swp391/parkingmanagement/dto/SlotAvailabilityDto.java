package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class SlotAvailabilityDto {
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private long availableCount;
}
