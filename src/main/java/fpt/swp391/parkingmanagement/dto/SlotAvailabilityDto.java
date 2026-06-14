package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotAvailabilityDto {
    // ============================================================
    // BUILDING INFO (only populated at building level, null at floor/zone/slot)
    // ============================================================
    private String buildingId;
    private String buildingName;
    private String buildingAddress;
    private String buildingPhone;
    
    // Operating hours
    private LocalTime operatingStartTime;
    private LocalTime operatingEndTime;
    private String operatingHoursDisplay;
    
    // Parking rules/regulations
    private String parkingRules;
    
    // Supported vehicle types at building level
    private List<VehicleTypeOptionResponse> supportedVehicleTypes;
    
    // Pricing policies at building level
    private List<PricingPolicySummaryDto> pricingPolicies;
    
    // ============================================================
    // FLOOR INFO
    // ============================================================
    private String floorId;
    private String floorName;
    private Integer floorLevel;
    private String floorStatus;
    private String floorVehicleTypeId;
    private String floorVehicleTypeName;
    
    // ============================================================
    // ZONE INFO
    // ============================================================
    private String zoneId;
    private String zoneName;
    private String zoneStatus;
    
    // ============================================================
    // SLOT INFO
    // ============================================================
    private long totalSlots;
    private long availableSlots;
    private String slotId;
    private String slotName;
    private String slotStatus;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private long availableCount;
    
    private List<SlotAvailabilityDto> slots;

    // Reserved slot info (when slot is RESERVED)
    private String reservedByUserId;
    private String reservedByUsername;
    private String reservedByVehicleId;

    // Pricing for this vehicle type
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private Integer maxHours;
}
