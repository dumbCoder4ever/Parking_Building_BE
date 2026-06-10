package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorAvailabilityDto {
    private String floorId;
    private String floorName;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private long totalSlots;
    private long availableSlots;
}
