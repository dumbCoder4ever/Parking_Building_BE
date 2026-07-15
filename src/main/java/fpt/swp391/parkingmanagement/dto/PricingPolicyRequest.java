package fpt.swp391.parkingmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PricingPolicyRequest {

    @NotBlank(message = "Vehicle type ID is required")
    private String vehicleTypeId;

    @NotBlank(message = "Policy name is required")
    private String policyName;

    @NotNull(message = "Base price is required")
    private BigDecimal basePrice;

    @NotNull(message = "Hourly rate is required")
    private BigDecimal hourlyRate;

    private Integer maxHours;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private String pricingType;
}
