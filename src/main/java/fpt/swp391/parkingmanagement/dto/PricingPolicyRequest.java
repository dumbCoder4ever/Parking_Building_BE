package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.VehicleType;
import jakarta.validation.constraints.Min;
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
    //private VehicleType vehicleType;
    private String vehicleTypeId;
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
