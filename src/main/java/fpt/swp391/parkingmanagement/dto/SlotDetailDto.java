package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotDetailDto {
    private String slotId;
    private String slotName;
    private String slotStatus;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private long availableCount;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private Integer maxHours;
    private String reservedByUserId;
    private String reservedByUsername;
    private String reservedByVehicleId;
}
