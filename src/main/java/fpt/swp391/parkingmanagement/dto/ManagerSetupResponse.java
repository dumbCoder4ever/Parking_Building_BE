package fpt.swp391.parkingmanagement.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ManagerSetupResponse {

    private String id;
    private String parentId;
    private String name;
    private String type;
    private Integer level;
    private Integer maxCapacity;
    private Integer currentOccupancy;
    private Integer createdSlots;
    private Integer totalFloors;
    private Integer floorCount;
    private Integer zoneCount;
    private Integer slotCount;
    private String address;
    private String contactNumber;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private String note;
    private String status;
    private LocalTime operatingStartTime;
    private LocalTime operatingEndTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
