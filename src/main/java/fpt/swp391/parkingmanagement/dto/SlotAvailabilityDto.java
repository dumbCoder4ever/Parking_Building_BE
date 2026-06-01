package fpt.swp391.parkingmanagement.dto;

import lombok.Data;

@Data
public class SlotAvailabilityDto {
    private String floorId;
    private String floorName;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private long availableCount;
}
