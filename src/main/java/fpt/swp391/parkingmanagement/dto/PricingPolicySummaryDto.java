package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingPolicySummaryDto {
    private String policyId;
    private String vehicleTypeId;
    private String vehicleTypeName;
    private String pricingType;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private Integer maxHours;
}
