package fpt.swp391.parkingmanagement.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private String vehicleTypeId;
    private String vehicleTypeName;
    
    private LocalDateTime checkinTime;
    private LocalDateTime currentTime;
    private int parkingMinutes;
    private int parkingHours;
    
    private String sessionStatus;
    private String paymentStatus;
    
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
    private BigDecimal estimatedFee;
}
