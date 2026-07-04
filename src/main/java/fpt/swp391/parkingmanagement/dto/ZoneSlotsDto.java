package fpt.swp391.parkingmanagement.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneSlotsDto {
    private String buildingId;
    private String buildingName;
    private String zoneId;
    private String zoneName;
    private String zoneStatus;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String floorVehicleTypeId;
    private String floorVehicleTypeName;
    private long totalSlots;
    private long availableSlots;
    private List<SlotDetailDto> slots;
}
