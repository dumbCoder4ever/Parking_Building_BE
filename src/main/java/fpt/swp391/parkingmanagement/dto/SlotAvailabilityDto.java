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
public class SlotAvailabilityDto {
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String floorStatus;
    private String floorVehicleTypeId;
    private String floorVehicleTypeName;
    private String zoneId;
    private String zoneName;
    private String zoneStatus;
    private long totalSlots;
    private long availableSlots;
    private String slotId;
    private String slotName;
    private String slotStatus;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private long availableCount;
    
    private java.util.List<SlotAvailabilityDto> slots;

    // Reserved slot info (when slot is RESERVED)
    private String reservedByUserId;
    private String reservedByUsername;
    private String reservedByVehicleId;

    // Pricing for this vehicle type
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private Integer maxHours;
}
