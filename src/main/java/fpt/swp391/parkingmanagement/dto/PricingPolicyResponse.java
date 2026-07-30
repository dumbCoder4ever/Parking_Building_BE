package fpt.swp391.parkingmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PricingPolicyResponse {
    private String policyId;
    private String vehicleTypeId;
    private String policyName;
    private String pricingType;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private Integer maxHours;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private String effectiveStatus;
    private LocalDateTime createdAt;

    // vehicle type data
    private String typeName;
}
