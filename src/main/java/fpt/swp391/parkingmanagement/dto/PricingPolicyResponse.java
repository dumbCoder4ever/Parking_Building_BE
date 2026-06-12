package fpt.swp391.parkingmanagement.dto;

import fpt.swp391.parkingmanagement.entity.VehicleType;
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
    private BigDecimal overnightFee;
    private BigDecimal lostTicketFee;
    private BigDecimal peakHourMultiplier;
    private BigDecimal maxDailyFee;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private LocalDateTime createdAt;

    //vehicle type data
    private String typeName;

    // Tiered pricing
    private Integer tier1Hours;
    private BigDecimal tier1Price;
    private Integer tier2Hours;
    private BigDecimal tier2Price;
    private Integer tier3Hours;
    private BigDecimal tier3Price;
    private Integer tier4Hours;
    private BigDecimal tier4Price;
    private BigDecimal perDayPrice;
}
