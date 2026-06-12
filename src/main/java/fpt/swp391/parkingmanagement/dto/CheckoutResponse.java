package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class CheckoutResponse {
    private String sessionId;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;
    private LocalDateTime checkoutTime;
    private BigDecimal totalFee;
    private String paymentId;

    private String vehicleTypeId;
    private String vehicleTypeName;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal peakHourMultiplier;
    private BigDecimal maxDailyFee;
    private BigDecimal overnightFee;
    private BigDecimal lostTicketFee;
    private BigDecimal overnightCharge;
    private boolean lostTicketCharge;
    private int parkingHours;
    private int parkingMinutes;

    // Tiered pricing breakdown
    private List<PricingTierResponse> pricingTiers;
    private String feeExplanation;
}
