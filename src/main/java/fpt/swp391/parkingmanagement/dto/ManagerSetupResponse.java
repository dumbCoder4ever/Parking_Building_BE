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
    private Integer createdSlots;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private String status;
    private LocalTime operatingStartTime;
    private LocalTime operatingEndTime;
    private LocalDateTime createdAt;
}
