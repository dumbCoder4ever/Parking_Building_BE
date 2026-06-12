package fpt.swp391.parkingmanagement.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DriverCurrentSessionResponse {
    private String sessionId;
    private String ticketCode;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private String zoneId;
    private String zoneName;
    private String slotId;
    private String slotName;
    private String vehiclePlate;
    private String vehicleColor;
    private String vehicleBrand;
    private String vehicleModel;
    private LocalDateTime checkinTime;
    private LocalDateTime currentTime;
    private int parkingMinutes;
    private int parkingHours;
    private String sessionStatus;
    private String paymentStatus;

    private String vehicleTypeId;
    private String vehicleTypeName;

    // Tiered pricing
    private List<PricingTierResponse> pricingTiers;

    // Fee at current duration (tiered)
    private BigDecimal currentAccumulatedFee;
    private String currentFeeExplanation;

    // Legacy fields (kept for backward compatibility)
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal peakHourMultiplier;
    private BigDecimal maxDailyFee;
    private BigDecimal overnightFee;
    private BigDecimal estimatedFee;
    private int estimatedHours;
}
