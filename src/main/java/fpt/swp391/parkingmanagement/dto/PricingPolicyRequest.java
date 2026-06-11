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

    @NotBlank(message = "Correct Vehicle type ID is required")
    private String vehicleTypeId;

    @NotBlank(message = "Policy name is required")
    private String policyName;

    private String pricingType;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal overnightFee;
    private BigDecimal lostTicketFee;
    private BigDecimal peakHourMultiplier;
    private BigDecimal maxDailyFee;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    //private String status;
}
