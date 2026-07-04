package fpt.swp391.parkingmanagement.dto;

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
public class BuildingInfoDto {
    private String buildingId;
    private String name;
    private String address;
    private String contactNumber;
    private LocalTime operatingStartTime;
    private LocalTime operatingEndTime;
    private String operatingHoursDisplay;
    private String parkingRules;
    private long totalSlots;
    private long availableSlots;
    private List<String> vehicleTypes;
    private List<PricingPolicySummaryDto> pricingByType;
}
