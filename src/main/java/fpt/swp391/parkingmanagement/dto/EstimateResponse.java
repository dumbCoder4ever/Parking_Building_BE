package fpt.swp391.parkingmanagement.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EstimateResponse {

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

    private String vehicleTypeId;
    private String vehicleTypeName;
    private String vehiclePlate;

    private LocalDateTime checkinTime;
    private LocalDateTime estimatedCheckoutTime;

    private int parkingHours;
    private int parkingMinutes;

    private BigDecimal totalFee;

    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal peakHourMultiplier;
    private BigDecimal maxDailyFee;
    private BigDecimal overnightFee;
    private BigDecimal lostTicketFee;

    private List<PricingTierResponse> pricingTiers;
    private String feeExplanation;
}
