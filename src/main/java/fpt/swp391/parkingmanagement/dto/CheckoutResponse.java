package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    
    private int parkingHours;
    private int parkingMinutes;
    
    private String sessionStatus;
    private String paymentStatus;
}
