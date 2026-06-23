package fpt.swp391.parkingmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ParkingSessionResponse {
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
    private String vehicleTypeId;
    private String vehicleTypeName;
    private LocalDateTime checkinTime;
    private String checkinImageUrl;
    
    private BigDecimal estimatedFee;
    private BigDecimal basePrice;
    private BigDecimal hourlyRate;
}
